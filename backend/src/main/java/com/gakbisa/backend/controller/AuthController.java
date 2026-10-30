package com.gakbisa.backend.controller;

import com.gakbisa.backend.dto.RegisterRequest;
import com.gakbisa.backend.dto.UserDto;
import com.gakbisa.backend.service.AuthService;
import com.gakbisa.backend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService auth;
    private final UserService userService;

    public AuthController(AuthService auth, UserService userService) {
        this.auth = auth;
        this.userService = userService;
    }

    /** Registrasi akun mahasiswa baru (role STUDENT, password BCrypt). */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto register(@Valid @RequestBody RegisterRequest req) {
        return auth.register(req);
    }

    @GetMapping("/me")
    public UserDto me() {
        return UserDto.from(userService.require(CurrentUser.id()));
    }
}
