package com.gakbisa.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration class that creates beans for Supabase REST API communication.
 * Uses environment variables loaded from .env by DotenvInitializer.
 */
@Configuration
public class SupabaseConfig {

    private final Environment env;

    public SupabaseConfig(Environment env) {
        this.env = env;
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public String supabaseUrl() {
        return env.getProperty("SUPABASE_URL", "");
    }

    @Bean
    public String supabaseAnonKey() {
        return env.getProperty("SUPABASE_ANON_KEY", "");
    }

    @Bean
    public String supabaseServiceKey() {
        return env.getProperty("SUPABASE_SERVICE_KEY", "");
    }
}
