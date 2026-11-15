package com.gakbisa.backend.controller;

import com.gakbisa.backend.dto.UserDto;
import com.gakbisa.backend.service.UserService;
import org.springframework.web.bind.annotation.*;

/** Ubah profil milik sendiri. (GET /api/me ada di AuthController.) */
@RestController
@RequestMapping("/api/me")
public class MeController {

    public record ProfileUpdateRequest(String fullName, String bio, String campus) {}

    private final UserService users;

    public MeController(UserService users) {
        this.users = users;
    }

    @PutMapping
    public UserDto update(@RequestBody ProfileUpdateRequest req) {
        var id = CurrentUser.id();
        return UserDto.from(users.updateProfile(id, id, req.fullName(), req.bio(), req.campus()));
    }
}
