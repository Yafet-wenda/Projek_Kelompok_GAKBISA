package com.gakbisa.backend.controller;

import com.gakbisa.backend.dto.UserDto;
import com.gakbisa.backend.service.UserService;
import com.gakbisa.backend.service.ProfileMediaService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Ubah profil milik sendiri. (GET /api/me ada di AuthController.) */
@RestController
@RequestMapping("/api/me")
public class MeController {

    public record ProfileUpdateRequest(String fullName, String bio, String campus) {}

    private final UserService users;
    private final ProfileMediaService profileMedia;

    public MeController(UserService users, ProfileMediaService profileMedia) {
        this.users = users;
        this.profileMedia = profileMedia;
    }

    @PutMapping
    public UserDto update(@RequestBody ProfileUpdateRequest req) {
        var id = CurrentUser.id();
        return UserDto.from(users.updateProfile(id, id, req.fullName(), req.bio(), req.campus()));
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserDto uploadAvatar(@RequestParam("file") MultipartFile file) {
        return UserDto.from(profileMedia.uploadAvatar(CurrentUser.id(), file));
    }

    @PostMapping(value = "/banner", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserDto uploadBanner(@RequestParam("file") MultipartFile file) {
        return UserDto.from(profileMedia.uploadBanner(CurrentUser.id(), file));
    }
}
