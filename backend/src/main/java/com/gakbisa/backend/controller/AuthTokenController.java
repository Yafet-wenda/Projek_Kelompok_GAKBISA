package com.gakbisa.backend.controller;

import com.gakbisa.backend.dto.UserDto;
import com.gakbisa.backend.service.LoginService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthTokenController {

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}

    public record LoginResponse(String accessToken, String tokenType, long expiresIn, UserDto user) {}

    private final LoginService login;

    public AuthTokenController(LoginService login) {
        this.login = login;
    }

    /** Login: kembalikan JWT. Kirim di header "Authorization: Bearer <accessToken>". */
    @PostMapping("/auth/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        var r = login.login(req.email(), req.password());
        return new LoginResponse(r.token().token(), "Bearer", r.token().expiresInSeconds(), UserDto.from(r.user()));
    }

    /** Cek cepat bahwa API hidup (publik). */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
