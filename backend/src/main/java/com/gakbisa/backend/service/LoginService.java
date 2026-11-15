package com.gakbisa.backend.service;

import com.gakbisa.backend.config.AppExceptions.ForbiddenException;
import com.gakbisa.backend.config.AppExceptions.UnauthorizedException;
import com.gakbisa.backend.domain.AccountStatus;
import com.gakbisa.backend.domain.User;
import com.gakbisa.backend.repository.UserRepository;
import com.gakbisa.backend.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Verifikasi email + password (BCrypt) lalu menerbitkan JWT. */
@Service
public class LoginService {

    public record LoginResult(User user, JwtService.IssuedToken token) {}

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public LoginService(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @Transactional(readOnly = true)
    public LoginResult login(String email, String password) {
        // Pesan sengaja sama untuk email tak dikenal & password salah (tidak membocorkan akun mana yang ada).
        User u = users.findByEmail(email == null ? "" : email.toLowerCase().trim())
            .orElseThrow(() -> new UnauthorizedException("Email atau password salah"));
        if (password == null || !encoder.matches(password, u.getPasswordHash())) {
            throw new UnauthorizedException("Email atau password salah");
        }
        if (u.getStatus() != AccountStatus.ACTIVE) {
            throw new ForbiddenException("Akun ditangguhkan. Hubungi admin.");
        }
        return new LoginResult(u, jwt.issue(u));
    }
}
