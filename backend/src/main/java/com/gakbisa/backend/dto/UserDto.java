package com.gakbisa.backend.dto;

import com.gakbisa.backend.domain.User;

import java.time.Instant;
import java.util.UUID;

public record UserDto(
    UUID id,
    String fullName,
    String email,
    String role,
    String bio,
    String campus,
    String status,
    Instant createdAt
) {
    public static UserDto from(User u) {
        return new UserDto(u.getId(), u.getFullName(), u.getEmail(), u.getRole().name(),
            u.getBio(), u.getCampus(), u.getStatus().name(), u.getCreatedAt());
    }
}
