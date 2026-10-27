package com.gakbisa.backend.dto;

import com.gakbisa.backend.domain.BarterRequest;

import java.time.Instant;
import java.util.UUID;

public record BarterDto(
    UUID id,
    UUID requesterId,
    String requesterName,
    UUID targetUserId,
    String targetUserName,
    String offeredSkill,
    String wantedSkill,
    String message,
    String status,
    Instant createdAt,
    Instant updatedAt
) {
    public static BarterDto from(BarterRequest b) {
        return new BarterDto(b.getId(),
            b.getRequester().getId(), b.getRequester().getFullName(),
            b.getTargetUser().getId(), b.getTargetUser().getFullName(),
            b.getOfferedUserSkill().getSkill().getName(),
            b.getWantedUserSkill().getSkill().getName(),
            b.getMessage(), b.getStatus().name(), b.getCreatedAt(), b.getUpdatedAt());
    }
}
