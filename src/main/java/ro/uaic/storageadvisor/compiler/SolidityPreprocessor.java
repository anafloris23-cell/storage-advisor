package ro.uaic.storageadvisor.compiler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Preprocesează sursa Solidity pentru analiză izolată a layout-ului unui singur fișier.
 *
 * Transformările aplicate, în ordine:
 *   1. Comentează liniile `import "...";`.
 *   2. Comentează directivele `using X for Y;`.
 *   3. Elimină listele de moștenire din declarații: `contract X is A, B { ... }` → `contract X { ... }`.
 *   4. Comentează apelurile de constructor către părinți din header-ul de `constructor(...)`.
 *   5. Golește corpurile funcțiilor / constructor-elor / modifier-elor / fallback / receive
 *      cu un placeholder neutru (eliminând astfel referințele la simboluri externe din corpuri).
 *   6. Detectează identificatorii capitalizați referiți după strip care nu sunt declarați local
 *      (tipuri externe) și adaugă stub-uri minime (`interface Name {}`) la finalul fișierului.
 *
 * Această abordare permite ca StorageAdvisor să compileze contracte cu imports și moșteniri
 * fără a avea fișierele dependente la dispoziție. Layout-ul rezultat reflectă DOAR variabilele
 * declarate explicit în acest fișier.
 *
 * Limitări:
 *   - Indicii de slot raportați sunt relativi la variabilele declarate explicit.
 *   - Procesarea e bazată pe regex + parser de paranteze (string-aware, comment-aware).
 *   - Identificatorii local declarați includ: contract, interface, library, struct, enum,
 *     error, event, function, modifier — plus valorile enum.
 */
public final class SolidityPreprocessor {

    private static final Pattern IMPORT_LINE =
            Pattern.compile("^\\s*import\\b.*?;\\s*$", Pattern.DOTALL);

    /** Pragma cu versiune fixă (ex: `pragma solidity 0.8.15;`) — fără operator caret. */
    private static final Pattern PRAGMA_FIXED =
            Pattern.compile("(pragma\\s+solidity\\s+)(\\d+\\.\\d+\\.\\d+)\\s*;");

    private static final Pattern USING_DIRECTIVE =
            Pattern.compile("^\\s*using\\b.*?;\\s*$", Pattern.MULTILINE);

    private static final Pattern INHERITANCE_HEADER =
            Pattern.compile(
                    "((?:abstract\\s+)?(?:contract|interface|library))\\s+(\\w+)\\s+is\\s+([^{;]+?)(\\s*\\{)",
                    Pattern.MULTILINE
            );

    private static final Pattern CONSTRUCTOR_HEADER =
            Pattern.compile(
                    "(constructor\\s*\\([^)]*\\))([^{]*?)(\\{)",
                    Pattern.DOTALL
            );

    private static final Pattern PARENT_CALL_IN_HEADER =
            Pattern.compile("\\b([A-Z][A-Za-z0-9_]*)\\s*\\([^()]*\\)");

    private static final Pattern BODY_KEYWORD =
            Pattern.compile("\\b(function|constructor|modifier|fallback|receive)\\b");

    private static final Pattern LOCAL_TYPE_DECL = Pattern.compile(
            "\\b(?:abstract\\s+)?(?:contract|interface|library)\\s+(\\w+)"
                    + "|\\bstruct\\s+(\\w+)"
                    + "|\\benum\\s+(\\w+)\\s*\\{([^}]*)\\}"
                    + "|\\berror\\s+(\\w+)"
                    + "|\\bevent\\s+(\\w+)"
                    + "|\\bfunction\\s+(\\w+)"
                    + "|\\bmodifier\\s+(\\w+)"
    );

    private static final Pattern STATE_VAR_DECL = Pattern.compile(
            "(?m)^(\\s*)([A-Za-z_]\\w*)(\\s+(?:public|private|internal|external)"
                    + "(?:\\s+(?:constant|immutable))*)\\s+(\\w+)\\s*([;=])"
    );

    /** Declarație de array în state var: `Foo[] public x;` / `Foo[3] x;`. */
    private static final Pattern ARRAY_VAR_DECL = Pattern.compile(
            "(?m)^(\\s*)([A-Za-z_]\\w*)((?:\\s*\\[\\s*\\w*\\s*\\])+)"
                    + "(\\s+(?:public|private|internal|external)(?:\\s+(?:constant|immutable))*)\\s+(\\w+)\\s*([;=])"
    );

    /**
     * Tag NatSpec {@code @inheritdoc X}. După ce scoatem moștenirile și import-urile,
     * contractul {@code X} referit nu mai există, iar solc dă {@code DocstringParsingError}.
     */
    private static final Pattern INHERITDOC_TAG =
            Pattern.compile("@inheritdoc\\s+[A-Za-z_][\\w.]*");

    private static final Set<String> SOLIDITY_VALUE_TYPES = buildSolidityValueTypes();

    private static Set<String> buildSolidityValueTypes() {
        Set<String> s = new HashSet<>(Set.of(
                "bool", "address", "string", "bytes", "byte", "fixed", "ufixed",
                "uint", "int", "mapping"
        ));
        for (int i = 8; i <= 256; i += 8) {
            s.add("uint" + i);
            s.add("int" + i);
        }
        for (int i = 1; i <= 32; i++) {
            s.add("bytes" + i);
        }
        return Set.copyOf(s);
    }

    /** Cuvinte cheie sau identificatori care nu trebuie tratați ca tipuri externe în declarații. */
    private static final Set<String> NON_TYPE_KEYWORDS = Set.of(
            "function", "constructor", "modifier", "event", "error", "struct", "enum",
            "using", "contract", "interface", "library", "abstract", "pragma", "import",
            "return", "if", "for", "while", "do", "break", "continue", "throw",
            "emit", "new", "delete", "try", "catch", "revert", "require", "assert"
    );

    /** Cuvinte cheie permise în signatura unei funcții, între `)` și `{`. */
    private static final Set<String> SIGNATURE_KEYWORDS = Set.of(
            "external", "public", "internal", "private",
            "view", "pure", "payable",
            "virtual", "override",
            "returns"
    );

    public Result process(String source) {
        List<String> warnings = new ArrayList<>();
        int importsCommented = 0;
        int inheritanceRemoved = 0;
        int usingsCommented = 0;
        int parentCallsCommented = 0;
        int bodiesStripped = 0;
        boolean pragmaRelaxed = false;

        // 0. relax pragma cu versiune fixă: `pragma solidity X.Y.Z;` → `pragma solidity ^X.Y.Z;`
        Matcher pragmaMatcher = PRAGMA_FIXED.matcher(source);
        if (pragmaMatcher.find()) {
            source = pragmaMatcher.replaceFirst("$1^$2;");
            pragmaRelaxed = true;
        }

        // 0.5 elimină tag-urile @inheritdoc (referă contracte din import-uri/moșteniri eliminate)
        int inheritdocRemoved = 0;
        Matcher inheritdocMatcher = INHERITDOC_TAG.matcher(source);
        StringBuffer afterInheritdoc = new StringBuffer();
        while (inheritdocMatcher.find()) {
            inheritdocRemoved++;
            inheritdocMatcher.appendReplacement(afterInheritdoc, "");
        }
        inheritdocMatcher.appendTail(afterInheritdoc);
        source = afterInheritdoc.toString();

        // 1. imports
        StringBuilder afterImports = new StringBuilder();
        for (String line : source.split("\\R", -1)) {
            if (IMPORT_LINE.matcher(line).matches()) {
                afterImports.append("// [storageadvisor] removed: ").append(line.trim()).append('\n');
                importsCommented++;
            } else {
                afterImports.append(line).append('\n');
            }
        }
        String working = afterImports.toString();

        // 2. using
        Matcher usingMatcher = USING_DIRECTIVE.matcher(working);
        StringBuffer afterUsing = new StringBuffer();
        while (usingMatcher.find()) {
            usingsCommented++;
            String orig = usingMatcher.group().trim();
            usingMatcher.appendReplacement(afterUsing,
                    Matcher.quoteReplacement("// [storageadvisor] removed: " + orig));
        }
        usingMatcher.appendTail(afterUsing);
        working = afterUsing.toString();

        // 3. inheritance
        Matcher inheritMatcher = INHERITANCE_HEADER.matcher(working);
        StringBuffer afterInherit = new StringBuffer();
        while (inheritMatcher.find()) {
            String keyword = inheritMatcher.group(1);
            String name = inheritMatcher.group(2);
            String parents = inheritMatcher.group(3).trim();
            String brace = inheritMatcher.group(4);
            inheritanceRemoved++;
            String replacement = Matcher.quoteReplacement(
                    keyword + " " + name + " /* [storageadvisor] removed: is " + parents + " */" + brace
            );
            inheritMatcher.appendReplacement(afterInherit, replacement);
        }
        inheritMatcher.appendTail(afterInherit);
        working = afterInherit.toString();

        // 4. parent calls in constructor header
        Matcher ctorMatcher = CONSTRUCTOR_HEADER.matcher(working);
        StringBuffer afterCtor = new StringBuffer();
        while (ctorMatcher.find()) {
            String head = ctorMatcher.group(1);
            String between = ctorMatcher.group(2);
            String brace = ctorMatcher.group(3);

            Matcher parentMatcher = PARENT_CALL_IN_HEADER.matcher(between);
            StringBuffer cleanedBetween = new StringBuffer();
            while (parentMatcher.find()) {
                parentCallsCommented++;
                parentMatcher.appendReplacement(cleanedBetween,
                        Matcher.quoteReplacement("/* removed: " + parentMatcher.group() + " */"));
            }
            parentMatcher.appendTail(cleanedBetween);

            ctorMatcher.appendReplacement(afterCtor,
                    Matcher.quoteReplacement(head + cleanedBetween + brace));
        }
        ctorMatcher.appendTail(afterCtor);
        working = afterCtor.toString();

        // 5. strip function/constructor/modifier/fallback/receive bodies
        BodyStripResult stripped = stripBodies(working);
        working = stripped.source;
        bodiesStripped = stripped.count;

        // 6. în corpurile contractelor: înlocuiesc tipurile externe din state vars cu `address`.
        Set<String> localTypes = collectLocalIdentifiers(working);
        Set<String> replacedTypes = new TreeSet<>();
        working = replaceExternalTypesInContractBodies(working, localTypes, replacedTypes);

        // 7. comentez modifier-urile externe din signaturi de funcții.
        Set<String> localModifiers = collectLocalModifiers(working);
        ModifierStripResult modStrip = stripExternalModifiersInSignatures(working, localModifiers);
        working = modStrip.source;
        int modifiersStripped = modStrip.count;

        if (importsCommented > 0 || inheritanceRemoved > 0 || usingsCommented > 0
                || parentCallsCommented > 0 || bodiesStripped > 0
                || !replacedTypes.isEmpty() || modifiersStripped > 0 || inheritdocRemoved > 0) {
            warnings.add(String.format(
                    "Isolated analysis: %d imports, %d inheritance lists, %d using directives, %d parent calls, "
                            + "%d function bodies stripped, %d external modifiers removed from signatures, "
                            + "%d @inheritdoc tags removed, "
                            + "%d external types replaced with address in state vars/mapping/array. "
                            + "The layout reflects ONLY the variables declared in this file.",
                    importsCommented, inheritanceRemoved, usingsCommented,
                    parentCallsCommented, bodiesStripped, modifiersStripped,
                    inheritdocRemoved, replacedTypes.size()
            ));
            if (!replacedTypes.isEmpty()) {
                warnings.add("External types replaced with `address`: " + String.join(", ", replacedTypes));
            }
        }

        return new Result(working, importsCommented, inheritanceRemoved, usingsCommented,
                parentCallsCommented, bodiesStripped, replacedTypes, warnings);
    }

    // ──────────────────────────────────────────────────────────────────
    // External modifier stripping from function signatures
    // ──────────────────────────────────────────────────────────────────

    private record ModifierStripResult(String source, int count) {}

    private static Set<String> collectLocalModifiers(String source) {
        Set<String> mods = new HashSet<>();
        Pattern p = Pattern.compile("\\bmodifier\\s+(\\w+)");
        Matcher m = p.matcher(source);
        while (m.find()) {
            mods.add(m.group(1));
        }
        return mods;
    }

    private static ModifierStripResult stripExternalModifiersInSignatures(
            String source, Set<String> localModifiers
    ) {
        StringBuilder out = new StringBuilder();
        int cursor = 0;
        int count = 0;
        Pattern funcPattern = Pattern.compile("\\bfunction\\b");
        Matcher m = funcPattern.matcher(source);
        while (m.find(cursor)) {
            int kwStart = m.start();
            if (isInsideCommentOrString(source, kwStart)) {
                out.append(source, cursor, m.end());
                cursor = m.end();
                continue;
            }
            // Caut `(` care deschide lista de parametri.
            int i = m.end();
            while (i < source.length() && source.charAt(i) != '(') i++;
            if (i >= source.length()) break;
            // Skip params: găsesc `)` matching.
            int parenDepth = 0;
            while (i < source.length()) {
                char c = source.charAt(i);
                if (c == '(') parenDepth++;
                else if (c == ')') {
                    parenDepth--;
                    if (parenDepth == 0) { i++; break; }
                }
                i++;
            }
            // segmentStart = imediat după `)` final al params.
            int segmentStart = i;
            // Caut `{` sau `;` (skip nested parens, comments, strings).
            while (i < source.length()) {
                char c = source.charAt(i);
                if (c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '/') {
                    while (i < source.length() && source.charAt(i) != '\n') i++;
                    continue;
                }
                if (c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '*') {
                    i += 2;
                    while (i + 1 < source.length()
                            && !(source.charAt(i) == '*' && source.charAt(i + 1) == '/')) i++;
                    i += 2;
                    continue;
                }
                if (c == '"' || c == '\'') {
                    i = skipString(source, i);
                    continue;
                }
                if (c == '(') {
                    int depth = 1;
                    i++;
                    while (i < source.length() && depth > 0) {
                        if (source.charAt(i) == '(') depth++;
                        else if (source.charAt(i) == ')') depth--;
                        i++;
                    }
                    continue;
                }
                if (c == '{' || c == ';') break;
                i++;
            }
            String segment = source.substring(segmentStart, i);
            int[] stripped = new int[1];
            String processedSegment = stripExternalIdentifiersInSegment(segment, localModifiers, stripped);
            count += stripped[0];

            out.append(source, cursor, segmentStart);
            out.append(processedSegment);
            cursor = i;
        }
        if (cursor < source.length()) out.append(source.substring(cursor));
        return new ModifierStripResult(out.toString(), count);
    }

    private static String stripExternalIdentifiersInSegment(
            String segment, Set<String> localModifiers, int[] strippedCount
    ) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < segment.length()) {
            char c = segment.charAt(i);
            if (Character.isJavaIdentifierStart(c)) {
                int start = i;
                while (i < segment.length() && Character.isJavaIdentifierPart(segment.charAt(i))) i++;
                String ident = segment.substring(start, i);
                // Verifică dacă urmează `(...)` (argumente pentru modifier sau returns).
                int afterIdent = i;
                while (afterIdent < segment.length() && Character.isWhitespace(segment.charAt(afterIdent))) afterIdent++;
                int argEnd = afterIdent;
                if (afterIdent < segment.length() && segment.charAt(afterIdent) == '(') {
                    int depth = 1;
                    argEnd = afterIdent + 1;
                    while (argEnd < segment.length() && depth > 0) {
                        char cc = segment.charAt(argEnd);
                        if (cc == '(') depth++;
                        else if (cc == ')') depth--;
                        argEnd++;
                    }
                }
                String fullCall = segment.substring(start, argEnd);

                if (SIGNATURE_KEYWORDS.contains(ident) || localModifiers.contains(ident)) {
                    out.append(fullCall);
                } else {
                    out.append("/* removed: ").append(fullCall.replace("*/", "*-/")).append(" */");
                    strippedCount[0]++;
                }
                i = argEnd;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    // ──────────────────────────────────────────────────────────────────
    // External type replacement in state-variable declarations
    // ──────────────────────────────────────────────────────────────────

    private static final Pattern CONTRACT_OPEN = Pattern.compile(
            "\\b(?:abstract\\s+)?(?:contract|interface|library)\\s+\\w+"
                    + "(?:\\s*/\\*[^*]*(?:\\*(?!/)[^*]*)*\\*/)?\\s*\\{",
            Pattern.DOTALL
    );

    private static String replaceExternalTypesInContractBodies(
            String source, Set<String> localTypes, Set<String> replacedTypes
    ) {
        StringBuilder out = new StringBuilder();
        int cursor = 0;
        Matcher m = CONTRACT_OPEN.matcher(source);
        while (m.find(cursor)) {
            int openBrace = m.end() - 1;
            int closeBrace = findMatchingBrace(source, openBrace);
            if (closeBrace < 0) break;

            out.append(source, cursor, openBrace + 1);
            String body = source.substring(openBrace + 1, closeBrace);
            String transformed = replaceTypesInBody(body, localTypes, replacedTypes);
            out.append(transformed);
            out.append('}');
            cursor = closeBrace + 1;
        }
        if (cursor < source.length()) {
            out.append(source.substring(cursor));
        }
        return out.toString();
    }

    private static String replaceTypesInBody(
            String body, Set<String> localTypes, Set<String> replacedTypes
    ) {
        body = replaceSimpleStateVarTypes(body, localTypes, replacedTypes);
        body = replaceMappingTypes(body, localTypes, replacedTypes);
        body = replaceArrayVarTypes(body, localTypes, replacedTypes);
        return body;
    }

    /** Declarații simple `Foo public x;` → `address public x;` pentru tipuri externe. */
    private static String replaceSimpleStateVarTypes(
            String body, Set<String> localTypes, Set<String> replacedTypes
    ) {
        Matcher m = STATE_VAR_DECL.matcher(body);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            String indent = m.group(1);
            String type = m.group(2);
            String visAndMods = m.group(3);
            String name = m.group(4);
            String terminator = m.group(5);

            if (isExternalType(type, localTypes)) {
                replacedTypes.add(type);
                m.appendReplacement(out, Matcher.quoteReplacement(
                        indent + "address" + visAndMods + " " + name + terminator
                ));
            }
        }
        m.appendTail(out);
        return out.toString();
    }

    /** Array-uri `Foo[] public x;` → `address[] public x;` pentru tipuri externe. */
    private static String replaceArrayVarTypes(
            String body, Set<String> localTypes, Set<String> replacedTypes
    ) {
        Matcher m = ARRAY_VAR_DECL.matcher(body);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            String indent = m.group(1);
            String type = m.group(2);
            String dims = m.group(3);
            String visAndMods = m.group(4);
            String name = m.group(5);
            String terminator = m.group(6);

            if (isExternalType(type, localTypes)) {
                replacedTypes.add(type);
                m.appendReplacement(out, Matcher.quoteReplacement(
                        indent + "address" + dims + visAndMods + " " + name + terminator
                ));
            }
        }
        m.appendTail(out);
        return out.toString();
    }

    /**
     * Înlocuiește tipurile externe din interiorul declarațiilor `mapping(...)` cu `address`.
     * Sigur pentru analiza de storage: un mapping ocupă mereu exact 1 slot, indiferent de
     * tipul cheii/valorii. Tratează și mapping-urile imbricate (`mapping(A => mapping(B => C))`).
     */
    private static String replaceMappingTypes(
            String body, Set<String> localTypes, Set<String> replacedTypes
    ) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        int n = body.length();
        while (i < n) {
            char c = body.charAt(i);
            // Sari peste comentarii/string-uri fără a le atinge.
            if (c == '/' && i + 1 < n && body.charAt(i + 1) == '/') {
                int j = i;
                while (j < n && body.charAt(j) != '\n') j++;
                out.append(body, i, j);
                i = j;
                continue;
            }
            if (c == '/' && i + 1 < n && body.charAt(i + 1) == '*') {
                int j = i + 2;
                while (j + 1 < n && !(body.charAt(j) == '*' && body.charAt(j + 1) == '/')) j++;
                j = Math.min(j + 2, n);
                out.append(body, i, j);
                i = j;
                continue;
            }
            if (c == '"' || c == '\'') {
                int j = Math.min(skipString(body, i), n);
                out.append(body, i, j);
                i = j;
                continue;
            }
            if (matchesWordAt(body, i, "mapping")) {
                int p = i + "mapping".length();
                int q = p;
                while (q < n && Character.isWhitespace(body.charAt(q))) q++;
                if (q < n && body.charAt(q) == '(') {
                    int close = findMatchingParen(body, q);
                    if (close > q) {
                        out.append(body, i, q + 1); // "mapping" + spații + "("
                        String inner = body.substring(q + 1, close);
                        out.append(replaceIdentifiersWithAddress(inner, localTypes, replacedTypes));
                        out.append(')');
                        i = close + 1;
                        continue;
                    }
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    /**
     * Înlocuiește tipurile externe din interiorul unui mapping cu `address`, înlocuind DOAR
     * tipurile, nu și numele opționale ale cheii/valorii (sintaxa Solidity ≥0.8.18:
     * {@code mapping(address account => uint256 balance)}). Tratează nume calificate
     * ({@code IFace.Type}) și mapping-uri imbricate.
     *
     * Mașină de stări: într-un mapping, primul identificator (sau cel de după {@code =>} / {@code (})
     * e TIPUL; un identificator imediat următor e NUMELE (se păstrează).
     */
    private static String replaceIdentifiersWithAddress(
            String text, Set<String> localTypes, Set<String> replacedTypes
    ) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        int n = text.length();
        boolean expectingType = true; // un mapping începe mereu cu tipul cheii
        while (i < n) {
            char c = text.charAt(i);
            if (c == '=' && i + 1 < n && text.charAt(i + 1) == '>') {
                out.append("=>");          // separator cheie→valoare: urmează un tip
                i += 2;
                expectingType = true;
                continue;
            }
            if (c == '(') {                 // mapping imbricat: urmează tipul cheii
                out.append('(');
                i++;
                expectingType = true;
                continue;
            }
            if (c == ')') {                 // s-a încheiat un tip-valoare (mapping imbricat)
                out.append(')');
                i++;
                expectingType = false;
                continue;
            }
            if (Character.isJavaIdentifierStart(c)) {
                int start = i;
                while (i < n && Character.isJavaIdentifierPart(text.charAt(i))) i++;
                // Consumă și partea calificată: `.Ident.Ident...`
                int j = i;
                while (j < n) {
                    int k = j;
                    while (k < n && Character.isWhitespace(text.charAt(k))) k++;
                    if (k < n && text.charAt(k) == '.') {
                        k++;
                        while (k < n && Character.isWhitespace(text.charAt(k))) k++;
                        if (k < n && Character.isJavaIdentifierStart(text.charAt(k))) {
                            k++;
                            while (k < n && Character.isJavaIdentifierPart(text.charAt(k))) k++;
                            j = k;
                            continue;
                        }
                    }
                    break;
                }
                String base = text.substring(start, i);
                String full = text.substring(start, j);
                if (expectingType) {
                    if (isExternalType(base, localTypes)) {
                        out.append("address");
                        replacedTypes.add(base);
                    } else {
                        out.append(full);
                    }
                    expectingType = false;  // un identificator următor ar fi NUMELE
                } else {
                    out.append(full);        // numele opțional al cheii/valorii — se păstrează
                }
                i = j;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static boolean isExternalType(String type, Set<String> localTypes) {
        return !SOLIDITY_VALUE_TYPES.contains(type)
                && !localTypes.contains(type)
                && !NON_TYPE_KEYWORDS.contains(type);
    }

    private static boolean matchesWordAt(String s, int i, String word) {
        if (!s.startsWith(word, i)) return false;
        if (i > 0 && Character.isJavaIdentifierPart(s.charAt(i - 1))) return false;
        int after = i + word.length();
        if (after < s.length() && Character.isJavaIdentifierPart(s.charAt(after))) return false;
        return true;
    }

    private static int findMatchingParen(String s, int openIdx) {
        int depth = 1;
        int i = openIdx + 1;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '/') {
                while (i < s.length() && s.charAt(i) != '\n') i++;
                continue;
            }
            if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < s.length() && !(s.charAt(i) == '*' && s.charAt(i + 1) == '/')) i++;
                i += 2;
                continue;
            }
            if (c == '"' || c == '\'') {
                i = skipString(s, i);
                continue;
            }
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) return i;
            }
            i++;
        }
        return -1;
    }

    // ──────────────────────────────────────────────────────────────────
    // Body stripping
    // ──────────────────────────────────────────────────────────────────

    private record BodyStripResult(String source, int count) {}

    private static BodyStripResult stripBodies(String source) {
        StringBuilder out = new StringBuilder();
        int cursor = 0;
        int count = 0;

        Matcher kwMatcher = BODY_KEYWORD.matcher(source);
        while (kwMatcher.find(cursor)) {
            int kwStart = kwMatcher.start();
            String keyword = kwMatcher.group(1);

            // Verifică să nu fie în comentariu sau string (skip dacă da).
            if (isInsideCommentOrString(source, kwStart)) {
                out.append(source, cursor, kwMatcher.end());
                cursor = kwMatcher.end();
                continue;
            }

            // Caută `{` sau `;` la nivelul 0 de paranteze, după keyword.
            int j = kwMatcher.end();
            int parenDepth = 0;
            int delim = -1;
            while (j < source.length()) {
                char c = source.charAt(j);
                if (c == '/' && j + 1 < source.length() && source.charAt(j + 1) == '/') {
                    while (j < source.length() && source.charAt(j) != '\n') j++;
                    continue;
                }
                if (c == '/' && j + 1 < source.length() && source.charAt(j + 1) == '*') {
                    j += 2;
                    while (j + 1 < source.length() && !(source.charAt(j) == '*' && source.charAt(j + 1) == '/')) j++;
                    j += 2;
                    continue;
                }
                if (c == '"' || c == '\'') {
                    j = skipString(source, j);
                    continue;
                }
                if (c == '(') parenDepth++;
                else if (c == ')') parenDepth--;
                else if (parenDepth == 0 && (c == '{' || c == ';')) {
                    delim = j;
                    break;
                }
                j++;
            }

            if (delim < 0) {
                out.append(source, cursor, source.length());
                cursor = source.length();
                break;
            }

            if (source.charAt(delim) == ';') {
                // Declarație fără body (function abstractă, event, etc.).
                if ("function".equals(keyword)) {
                    // Ștergere integrală — funcțiile abstracte pot avea tipuri externe în signatură.
                    out.append(source, cursor, kwStart);
                    cursor = delim + 1;
                    count++;
                } else {
                    out.append(source, cursor, delim + 1);
                    cursor = delim + 1;
                }
                continue;
            }

            // `{` găsit la `delim`. Caut matching `}`.
            int endBrace = findMatchingBrace(source, delim);
            if (endBrace < 0) {
                out.append(source, cursor, source.length());
                cursor = source.length();
                break;
            }

            String header = source.substring(kwStart, delim);
            String replacement = chooseBody(keyword, header);

            if (replacement == null) {
                // Ștergere integrală (signature + body).
                out.append(source, cursor, kwStart);
            } else {
                out.append(source, cursor, delim + 1);  // include header + `{`
                out.append(replacement);
                out.append('}');
            }
            cursor = endBrace + 1;
            count++;
        }
        if (cursor < source.length()) {
            out.append(source.substring(cursor));
        }
        return new BodyStripResult(out.toString(), count);
    }

    /** Returnează corpul de înlocuire, sau `null` pentru a indica ștergere integrală a declarației. */
    private static String chooseBody(String keyword, String header) {
        return switch (keyword) {
            case "modifier" -> " _; ";
            case "constructor", "fallback", "receive" -> "";
            case "function" -> null;
            default -> "";
        };
    }

    // ──────────────────────────────────────────────────────────────────
    // Brace / string / comment utilities
    // ──────────────────────────────────────────────────────────────────

    private static int findMatchingBrace(String s, int openIdx) {
        int depth = 1;
        int i = openIdx + 1;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '/') {
                while (i < s.length() && s.charAt(i) != '\n') i++;
                continue;
            }
            if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < s.length() && !(s.charAt(i) == '*' && s.charAt(i + 1) == '/')) i++;
                i += 2;
                continue;
            }
            if (c == '"' || c == '\'') {
                i = skipString(s, i);
                continue;
            }
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
            i++;
        }
        return -1;
    }

    private static int skipString(String s, int startIdx) {
        char quote = s.charAt(startIdx);
        int i = startIdx + 1;
        while (i < s.length() && s.charAt(i) != quote) {
            if (s.charAt(i) == '\\' && i + 1 < s.length()) {
                i += 2;
                continue;
            }
            i++;
        }
        return i + 1;
    }

    private static boolean isInsideCommentOrString(String s, int idx) {
        // Aproximare: scan-uiește de la 0 până la idx și ține minte starea.
        int i = 0;
        while (i < idx) {
            char c = s.charAt(i);
            if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '/') {
                while (i < s.length() && s.charAt(i) != '\n' && i < idx) i++;
                if (i >= idx) return true;
                continue;
            }
            if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < s.length() && !(s.charAt(i) == '*' && s.charAt(i + 1) == '/')) {
                    if (i >= idx) return true;
                    i++;
                }
                i += 2;
                continue;
            }
            if (c == '"' || c == '\'') {
                int closing = skipString(s, i);
                if (idx < closing) return true;
                i = closing;
                continue;
            }
            i++;
        }
        return false;
    }

    // ──────────────────────────────────────────────────────────────────
    // Identifier collection
    // ──────────────────────────────────────────────────────────────────

    private static Set<String> collectLocalIdentifiers(String source) {
        Set<String> locals = new HashSet<>();
        Matcher m = LOCAL_TYPE_DECL.matcher(source);
        while (m.find()) {
            for (int g = 1; g <= m.groupCount(); g++) {
                String captured = m.group(g);
                if (captured == null) continue;
                if (g == 4) {
                    // enum values: split CSV.
                    for (String v : captured.split(",")) {
                        String trimmed = v.trim();
                        if (!trimmed.isEmpty()) locals.add(trimmed);
                    }
                } else {
                    locals.add(captured);
                }
            }
        }
        return locals;
    }

    @SuppressWarnings("unused")
    private static String stripCommentsAndStrings(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '/') {
                while (i < source.length() && source.charAt(i) != '\n') i++;
                continue;
            }
            if (c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < source.length() && !(source.charAt(i) == '*' && source.charAt(i + 1) == '/')) i++;
                i += 2;
                continue;
            }
            if (c == '"' || c == '\'') {
                i = skipString(source, i);
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    // ──────────────────────────────────────────────────────────────────
    // Result
    // ──────────────────────────────────────────────────────────────────

    public record Result(
            String processedSource,
            int importsCommented,
            int inheritanceRemoved,
            int usingsCommented,
            int parentCallsCommented,
            int bodiesStripped,
            Set<String> stubbedTypes,
            List<String> warnings
    ) {
        public boolean modified() {
            return importsCommented + inheritanceRemoved + usingsCommented
                    + parentCallsCommented + bodiesStripped + stubbedTypes.size() > 0;
        }
    }
}
