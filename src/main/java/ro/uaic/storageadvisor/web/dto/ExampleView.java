package ro.uaic.storageadvisor.web.dto;

/**
 * Un exemplu de contract expus prin {@code GET /api/examples}.
 *
 * @param id          identificator stabil, folosit ca cheie în interfață
 * @param title       numele afișat pe card
 * @param tag         categoria problemei demonstrate (ex: "Ordonare", "Struct")
 * @param kind        proveniența: "synthetic" (exemplu de demo) sau "real" (contract real)
 * @param description o linie scurtă despre ce demonstrează exemplul
 * @param source      codul Solidity propriu-zis, încărcat în editor la click
 */
public record ExampleView(
        String id,
        String title,
        String tag,
        String kind,
        String description,
        String source
) {}
