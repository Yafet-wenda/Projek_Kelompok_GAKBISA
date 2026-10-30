package com.gakbisa.backend.controller;

import com.gakbisa.backend.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Profil publik untuk katalog (use case aktor Tamu). Sengaja hanya berisi nama,
 * kampus, reputasi, dan daftar keahlian yang diajarkan — tidak ada email atau status akun.
 */
@RestController
@RequestMapping("/api/users")
public class PublicProfileController {

    private final UserService users;

    public PublicProfileController(UserService users) {
        this.users = users;
    }

    @GetMapping("/{id}")
    public UserService.PublicProfile profile(@PathVariable UUID id) {
        return users.publicProfile(id);
    }
}
