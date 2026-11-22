package com.gakbisa.backend.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/** Encoder & decoder JWT HS256. Kunci dibaca dari gakbisa.security.jwt-secret (env JWT_SECRET). */
@Configuration
public class JwtConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

    @Bean
    public SecretKey jwtSecretKey(@Value("${gakbisa.security.jwt-secret:}") String secret,
                                  Environment environment) {
        byte[] key;
        if (secret == null || secret.isBlank()) {
            if (java.util.Arrays.asList(environment.getActiveProfiles()).contains("prod")) {
                throw new IllegalStateException("JWT_SECRET wajib diisi saat profile prod aktif");
            }
            log.warn("JWT_SECRET kosong: memakai kunci acak sementara. Token hilang setiap restart. "
                + "Isi JWT_SECRET (min. 32 karakter) di .env untuk penggunaan normal.");
            key = new byte[32];
            new SecureRandom().nextBytes(key);
        } else {
            key = secret.getBytes(StandardCharsets.UTF_8);
            if (key.length < 32) {
                throw new IllegalStateException("JWT_SECRET minimal 32 karakter (sekarang " + key.length + ")");
            }
        }
        return new SecretKeySpec(key, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSecretKey).macAlgorithm(MacAlgorithm.HS256).build();
    }
}
