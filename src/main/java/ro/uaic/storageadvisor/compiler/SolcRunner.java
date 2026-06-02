package ro.uaic.storageadvisor.compiler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public class SolcRunner {

    private static final long TIMEOUT_SECONDS = 30;

    private final ObjectMapper mapper = new ObjectMapper();
    private final String solcExecutable;
    private final SolidityPreprocessor preprocessor = new SolidityPreprocessor();
    private SolidityPreprocessor.Result lastPreprocessResult;

    public SolcRunner() {
        this("solc");
    }

    public SolcRunner(String solcExecutable) {
        this.solcExecutable = solcExecutable;
    }

    public SolidityPreprocessor.Result lastPreprocessResult() {
        return lastPreprocessResult;
    }

    public JsonNode compile(Path solidityFile) throws Exception {
        String rawSource = Files.readString(solidityFile, StandardCharsets.UTF_8);
        SolidityPreprocessor.Result pre = preprocessor.process(rawSource);
        this.lastPreprocessResult = pre;
        String sourceCode = pre.processedSource();
        if (pre.modified()) {
            try {
                Path dump = solidityFile.resolveSibling(solidityFile.getFileName().toString() + ".preprocessed.sol");
                Files.writeString(dump, sourceCode, StandardCharsets.UTF_8);
            } catch (IOException ignored) {}
        }
        String inputJson = SolcInputBuilder.buildStandardJson(solidityFile.getFileName().toString(), sourceCode);

        ProcessBuilder processBuilder = new ProcessBuilder(solcExecutable, "--standard-json");

        Process process;
        try {
            process = processBuilder.start();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Nu s-a putut porni 'solc'. Verifică dacă este instalat și pe PATH (rulează: solc --version).", e);
        }

        try (OutputStream os = process.getOutputStream()) {
            os.write(inputJson.getBytes(StandardCharsets.UTF_8));
            os.flush();
        }

        StreamGobbler stdoutGobbler = new StreamGobbler(process.getInputStream());
        StreamGobbler stderrGobbler = new StreamGobbler(process.getErrorStream());
        Thread stdoutThread = new Thread(stdoutGobbler, "solc-stdout");
        Thread stderrThread = new Thread(stderrGobbler, "solc-stderr");
        stdoutThread.start();
        stderrThread.start();

        boolean finished;
        try {
            finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Întrerupt în timpul așteptării solc.", e);
        }

        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("solc a depășit timeout-ul de " + TIMEOUT_SECONDS + " secunde.");
        }

        try {
            stdoutThread.join(1000);
            stderrThread.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Întrerupt în timpul citirii output-ului solc.", e);
        }

        if (stdoutGobbler.getError() != null) {
            throw new IllegalStateException("Eroare la citirea stdout de la solc.", stdoutGobbler.getError());
        }
        if (stderrGobbler.getError() != null) {
            throw new IllegalStateException("Eroare la citirea stderr de la solc.", stderrGobbler.getError());
        }

        int exitCode = process.exitValue();
        String stdout = stdoutGobbler.getOutput();
        String stderr = stderrGobbler.getOutput();

        if (exitCode != 0) {
            throw new IllegalStateException("solc a eșuat cu exit code " + exitCode + ":\n" + stderr);
        }

        if (stdout.isBlank()) {
            throw new IllegalStateException("solc nu a produs output. stderr:\n" + stderr);
        }

        JsonNode root = mapper.readTree(stdout);
        if (root.has("errors")) {
            boolean hasError = false;
            StringBuilder sb = new StringBuilder();
            for (JsonNode error : root.get("errors")) {
                String severity = error.path("severity").asText();
                String message = error.path("formattedMessage").asText();
                sb.append(severity).append(": ").append(message).append(System.lineSeparator());
                if ("error".equalsIgnoreCase(severity)) {
                    hasError = true;
                }
            }
            if (hasError) {
                throw new IllegalStateException(sb.toString());
            }
        }

        return root;
    }

    private static final class StreamGobbler implements Runnable {
        private final InputStream stream;
        private volatile String output = "";
        private volatile IOException error;

        StreamGobbler(InputStream stream) {
            this.stream = stream;
        }

        @Override
        public void run() {
            try {
                output = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                error = e;
            }
        }

        String getOutput() {
            return output;
        }

        IOException getError() {
            return error;
        }
    }
}
