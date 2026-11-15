package com.gakbisa.backend.security;

import com.gakbisa.backend.domain.AccountStatus;
import com.gakbisa.backend.domain.User;
import com.gakbisa.backend.repository.UserRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Mengubah JWT yang valid menjadi Authentication dengan principal GakbisaUserDetails,
 * sehingga CurrentUser dan @PreAuthorize bekerja persis seperti saat masih memakai session.
 *
 * Pengguna dimuat dari database, tetapi hasilnya disimpan singkat di memori (TTL 15 detik)
 * agar setiap request tidak membayar satu query ekstra. Akibatnya akun yang ditangguhkan
 * atau berganti peran baru berlaku paling lama 15 detik kemudian.
 */
@Component
public class JwtUserAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final long TTL_NANOS = TimeUnit.SECONDS.toNanos(15);
    private static final int MAX_ENTRIES = 1000;

    private record Cached(GakbisaUserDetails details, long expiresAt) {}

    private final UserRepository users;
    private final Map<UUID, Cached> cache = new ConcurrentHashMap<>();

    public JwtUserAuthenticationConverter(UserRepository users) {
        this.users = users;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID id;
        try {
            id = UUID.fromString(jwt.getSubject());
        } catch (RuntimeException e) {
            throw new InvalidBearerTokenException("Token tidak valid");
        }
        GakbisaUserDetails details = load(id);
        return new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
    }

    private GakbisaUserDetails load(UUID id) {
        long now = System.nanoTime();
        Cached hit = cache.get(id);
        if (hit != null && now - hit.expiresAt() < 0) {
            return hit.details();
        }
        User user = users.findById(id)
            .orElseThrow(() -> new InvalidBearerTokenException("Akun tidak ditemukan"));
        if (user.getStatus() != AccountStatus.ACTIVE) {
            cache.remove(id);
            throw new InvalidBearerTokenException("Akun tidak aktif");
        }
        GakbisaUserDetails details = new GakbisaUserDetails(user);
        if (cache.size() >= MAX_ENTRIES) {
            cache.clear();
        }
        cache.put(id, new Cached(details, now + TTL_NANOS));
        return details;
    }
}