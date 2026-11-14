package com.gakbisa.backend.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;

/**
<<<<<<< HEAD
 * Loads the .env file (located at project root) and makes its entries available
 * as system properties so that Spring's {@code ${…}} placeholders can resolve
 * them (see {@code application.yml} import line).
=======
 * Loads the env file and publishes its entries as system properties so IDE runs
 * (tanpa start.ps1) tetap dapat kredensial.
>>>>>>> origin/main
 */
@Configuration
public class DotenvInitializer {

<<<<<<< HEAD
=======
    // System properties menang atas spring.config.import, jadi berkas yang dibaca di sini
    // harus berkas yang sama dengan yang dipilih start.ps1; kalau tidak, mode Supabase
    // diam-diam tertimpa .env lokal.
    private static final String ENV_FILE =
        System.getenv().getOrDefault("GAKBISA_ENV", ".env");

>>>>>>> origin/main
    @PostConstruct
    public void init() {
        Dotenv dotenv = Dotenv.configure()
                .directory("./")   // root of the backend module
<<<<<<< HEAD
=======
                .filename(ENV_FILE)
>>>>>>> origin/main
                .ignoreIfMissing()
                .load();
        // Populate System properties – Spring reads from System.getProperty
        dotenv.entries().forEach(e -> System.setProperty(e.getKey(), e.getValue()));
    }
}

