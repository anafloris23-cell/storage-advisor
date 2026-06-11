package ro.uaic.storageadvisor.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punctul de intrare pentru API-ul web StorageAdvisor.
 *
 * Pornire: {@code mvn spring-boot:run}
 * Serverul ascultă implicit pe http://localhost:8080 și expune endpoint-ul
 * {@code POST /api/analyze} consumat de frontend-ul React.
 *
 * Logica de analiză rămâne neschimbată — acest strat doar o expune prin HTTP.
 */
@SpringBootApplication(scanBasePackages = "ro.uaic.storageadvisor.web")
public class StorageAdvisorApplication {
    public static void main(String[] args) {
        SpringApplication.run(StorageAdvisorApplication.class, args);
    }
}
