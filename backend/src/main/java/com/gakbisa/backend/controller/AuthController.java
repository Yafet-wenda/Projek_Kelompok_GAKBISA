package com.gakbisa.backend.controller;

import com.gakbisa.backend.dto.RegisterRequest;
import com.gakbisa.backend.dto.UserDto;
import com.gakbisa.backend.service.AuthService;
import com.gakbisa.backend.service.UserService;
import com.gakbisa.backend.service.SupabaseAuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService auth;
    private final UserService userService;
    private final SupabaseAuthService supabaseAuthService;

    public AuthController(AuthService auth, UserService userService, SupabaseAuthService supabaseAuthService) {
        this.auth = auth;
        this.userService = userService;
        this.supabaseAuthService = supabaseAuthService;
    }

    /** Registrasi akun mahasiswa baru (role STUDENT, password BCrypt). */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto register(@Valid @RequestBody RegisterRequest req) {
        return auth.register(req);
    }

    /** Supabase sign-up (creates user in Supabase Auth). */
    @PostMapping("/supabase/signup")
    public Map<String, Object> supabaseSignUp(@RequestParam String email, @RequestParam String password) {
        return supabaseAuthService.signUp(email, password);
    }

    /** Supabase sign-in – returns JWT token. */
    @PostMapping("/supabase/signin")
    public String supabaseSignIn(@RequestParam String email, @RequestParam String password) {
        return supabaseAuthService.signIn(email, password);
    }

    @GetMapping("/me")
    public UserDto me() {
        return UserDto.from(userService.require(CurrentUser.id()));
    }
}
