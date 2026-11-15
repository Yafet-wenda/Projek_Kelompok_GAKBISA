package com.gakbisa.backend.security;

import com.gakbisa.backend.domain.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/** Menerbitkan access token. Isi token sengaja minim: id pengguna (sub) dan peran. */
@Service
public class JwtService {

    public record IssuedToken(String token, long expiresInSeconds) {}

    private final JwtEncoder encoder;
    private final Duration ttl;

    public JwtService(JwtEncoder encoder, @Value("${gakbisa.security.jwt-ttl-minutes:120}") long ttlMinutes) {
        this.encoder = encoder;
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    public IssuedToken issue(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer("gakbisa")
            .issuedAt(now)
            .expiresAt(now.plus(ttl))
            .subject(user.getId().toString())
            .claim("role", user.getRole().name())
            .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(value, ttl.toSeconds());
    }
}
