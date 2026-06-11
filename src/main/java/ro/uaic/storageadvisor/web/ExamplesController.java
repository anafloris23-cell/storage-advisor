package ro.uaic.storageadvisor.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.uaic.storageadvisor.web.dto.ErrorResponse;
import ro.uaic.storageadvisor.web.dto.ExampleView;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Expune contractele exemplu pentru interfață prin {@code GET /api/examples}.
 *
 */
@RestController
@RequestMapping("/api")
public class ExamplesController {

    private final ObjectMapper objectMapper;
    private final Path examplesDir;

    public ExamplesController(
            ObjectMapper objectMapper,
            @Value("${storageadvisor.examples.dir:examples/synthetic}") String examplesDir) {
        this.objectMapper = objectMapper;
        this.examplesDir = Path.of(examplesDir);
    }

    @GetMapping("/examples")
    public ResponseEntity<?> examples() {
        try {
            Path indexFile = examplesDir.resolve("examples.json");
            ExampleMeta[] metas = objectMapper.readValue(Files.readAllBytes(indexFile), ExampleMeta[].class);

            List<ExampleView> result = new ArrayList<>();
            for (ExampleMeta meta : metas) {
                String source = Files.readString(examplesDir.resolve(meta.file()), StandardCharsets.UTF_8);
                result.add(new ExampleView(meta.id(), meta.title(), meta.tag(), meta.kind(), meta.description(), source));
            }
            return ResponseEntity.ok(result);

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Nu am putut încărca exemplele: " + e.getMessage()));
        }
    }

    /** Intrarea din {@code examples.json}: metadate + numele fișierului sursă. */
    private record ExampleMeta(
            String id,
            String title,
            String tag,
            String kind,
            String description,
            String file
    ) {}
}
