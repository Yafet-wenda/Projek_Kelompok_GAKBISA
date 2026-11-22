package com.gakbisa.backend.security;

import com.gakbisa.backend.domain.AccountStatus;
import com.gakbisa.backend.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

final class WebSessionRefreshFilter extends OncePerRequestFilter {
    private final UserRepository users;
    private final HttpSessionSecurityContextRepository contexts = new HttpSessionSecurityContextRepository();

    WebSessionRefreshFilter(UserRepository users) {
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof GakbisaUserDetails details) {
            var user = users.findById(details.getUser().getId()).orElse(null);
            if (user == null || user.getStatus() != AccountStatus.ACTIVE) {
                SecurityContextHolder.clearContext();
                var session = request.getSession(false);
                if (session != null) session.invalidate();
            } else {
                // Hak akses dalam sesi tidak boleh bertahan setelah akun dibatasi atau perannya berubah.
                var fresh = new GakbisaUserDetails(user);
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                    fresh, null, fresh.getAuthorities()));
                SecurityContextHolder.setContext(context);
                contexts.saveContext(context, request, response);
            }
        }
        chain.doFilter(request, response);
    }
}
