package ro.uaic.storageadvisor.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Permite frontend-ului React (rulat de Vite pe localhost:5175) să apeleze API-ul
 * în timpul dezvoltării, când cele două rulează pe porturi diferite.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(
                        "http://localhost:5175",
                        "http://127.0.0.1:5175"
                )
                .allowedMethods("GET", "POST", "OPTIONS");
    }
}
