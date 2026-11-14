package com.gakbisa.backend.dto;

import com.gakbisa.backend.domain.LearningSession;

import java.time.Instant;
import java.util.UUID;

public record SessionDto(
    UUID id,
    UUID barterRequestId,
    UUID requesterId,
    UUID targetUserId,
    Instant scheduledAt,
    int durationMinutes,
    String meetingProvider,
    String meetingRoom,
    String meetingUrl,
    String status,
    Instant completedAt
) {
    public static SessionDto from(LearningSession s) {
        return new SessionDto(s.getId(), s.getBarterRequest().getId(),
            s.getBarterRequest().getRequester().getId(), s.getBarterRequest().getTargetUser().getId(),
            s.getScheduledAt(), s.getDurationMinutes(), s.getMeetingProvider().name(),
            s.getMeetingRoom(), s.getMeetingUrl(), s.getStatus().name(), s.getCompletedAt());
    }
}
