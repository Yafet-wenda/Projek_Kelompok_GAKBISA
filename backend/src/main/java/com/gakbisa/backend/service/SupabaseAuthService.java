package com.gakbisa.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Service that delegates authentication to Supabase Auth (GoTrue) via REST API.
 * No external SDK needed — uses Spring RestTemplate to call Supabase endpoints.
 */
@Service
public class SupabaseAuthService {

    private static final Logger log = LoggerFactory.getLogger(SupabaseAuthService.class);

    private final RestTemplate restTemplate;
    private final String supabaseUrl;
    private final String supabaseAnonKey;

    public SupabaseAuthService(RestTemplate restTemplate,
                               @Qualifier("supabaseUrl") String supabaseUrl,
                               @Qualifier("supabaseAnonKey") String supabaseAnonKey) {
        this.restTemplate = restTemplate;
        this.supabaseUrl = supabaseUrl;
        this.supabaseAnonKey = supabaseAnonKey;
    }

    /**
     * Register a new user in Supabase Auth.
     * Returns the full JSON response as a Map (contains user object, access_token, etc.).
     */
    public Map<String, Object> signUp(String email, String password) {
        String url = supabaseUrl + "/auth/v1/signup";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("apikey", supabaseAnonKey);

        Map<String, String> body = Map.of("email", email, "password", password);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, request, Map.class);
        log.info("Supabase signUp response status: {}", response.getStatusCode());
        return response.getBody();
    }

    /**
     * Sign in an existing user and obtain a JWT access token.
     * Returns the access_token string.
     */
    public String signIn(String email, String password) {
        String url = supabaseUrl + "/auth/v1/token?grant_type=password";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("apikey", supabaseAnonKey);

        Map<String, String> body = Map.of("email", email, "password", password);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, request, Map.class);
        log.info("Supabase signIn response status: {}", response.getStatusCode());

        Map<String, Object> responseBody = response.getBody();
        if (responseBody != null && responseBody.containsKey("access_token")) {
            return (String) responseBody.get("access_token");
        }
        throw new RuntimeException("Supabase sign-in failed: no access_token in response");
    }
}
