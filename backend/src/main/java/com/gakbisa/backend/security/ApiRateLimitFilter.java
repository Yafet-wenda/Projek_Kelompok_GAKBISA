package com.gakbisa.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Order(1)
public class ApiRateLimitFilter extends OncePerRequestFilter {

    private final Map<String, Deque<Long>> loginAttempts = new ConcurrentHashMap<>();
    private final Map<String, Deque<Long>> publicReads = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if ("POST".equalsIgnoreCase(request.getMethod()) && "/api/auth/login".equals(path)) {
            if (blocked(loginAttempts, clientKey(request), 10, 60_000L)) {
                writeLimit(response, "Terlalu banyak percobaan login");
                return;
            }
        } else if ("GET".equalsIgnoreCase(request.getMethod()) && isPublicEndpoint(path)) {
            if (blocked(publicReads, clientKey(request), 120, 60_000L)) {
                writeLimit(response, "Terlalu banyak permintaan publik");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private static boolean isPublicEndpoint(String path) {
        return "/api/health".equals(path)
            || "/api/skills".equals(path)
            || "/api/categories".equals(path)
            || "/api/portfolios".equals(path)
            || path.matches("/api/portfolios/[^/]+")
            || path.matches("/api/users/[^/]+");
    }

    private static boolean blocked(Map<String, Deque<Long>> buckets, String key,
                                   int limit, long windowMillis) {
        long now = Instant.now().toEpochMilli();
        Deque<Long> bucket = buckets.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (bucket) {
            while (!bucket.isEmpty() && now - bucket.peekFirst() > windowMillis) {
                bucket.removeFirst();
            }
            if (bucket.size() >= limit) return true;
            bucket.addLast(now);
            return false;
        }
    }

    private static String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static void writeLimit(HttpServletResponse response, String message) throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", "60");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"" + message + "\"}");
    }
}
