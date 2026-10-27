package com.gakbisa.backend.service;

import com.gakbisa.backend.config.AppExceptions.ValidationException;
import com.gakbisa.backend.domain.AccountStatus;
import com.gakbisa.backend.domain.User;
import com.gakbisa.backend.domain.UserRole;
import com.gakbisa.backend.dto.RegisterRequest;
import com.gakbisa.backend.dto.UserDto;
import com.gakbisa.backend.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public AuthService(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Transactional
    public UserDto register(RegisterRequest req) {
        String email = req.normalizedEmail();
        if (users.existsByEmail(email)) {
            // Pesan tidak membocorkan detail sensitif selain bahwa akun sudah ada (skenario uji #1)
            throw new ValidationException("Email sudah terdaftar. Silakan login.");
        }
        User u = new User();
        u.setFullName(req.fullName().trim());
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(req.password()));
        u.setRole(UserRole.STUDENT);
        u.setStatus(AccountStatus.ACTIVE);
        u.setCampus(req.campus() == null ? null : req.campus().trim());
        try {
            return UserDto.from(users.save(u));
        } catch (DataIntegrityViolationException e) {
            // Balapan dua request registrasi email sama -> constraint unique email
            throw new ValidationException("Email sudah terdaftar. Silakan login.");
        }
    }
}
