package com.gakbisa.backend.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;

/**
 * Loads the .env file (located at project root) and makes its entries available
 * as system properties so that Spring's {@code ${…}} placeholders can resolve
 * them (see {@code application.yml} import line).
 */
@Configuration
public class DotenvInitializer {

    @PostConstruct
    public void init() {
        Dotenv dotenv = Dotenv.configure()
                .directory("./")   // root of the backend module
                .ignoreIfMissing()
                .load();
        // Populate System properties – Spring reads from System.getProperty
        dotenv.entries().forEach(e -> System.setProperty(e.getKey(), e.getValue()));
    }
}

