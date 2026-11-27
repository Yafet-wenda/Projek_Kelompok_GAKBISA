package com.gakbisa.backend.dto;

import com.gakbisa.backend.domain.LearningSession;

import java.time.Instant;
import java.util.UUID;

public record SessionDto(
    UUID id,
    UUID barterRequestId,
    UUID groupId,
    UUID followupOfId,
    UUID requesterId,
    UUID targetUserId,
    Instant scheduledAt,
    int durationMinutes,
    String meetingProvider,
    String meetingRoom,
    String meetingUrl,
    String status,
    Instant completedAt,
    // Reschedule proposal fields
    UUID rescheduleProposedBy,
    Instant rescheduleProposedAt,
    Instant rescheduleProposedTime,
    Integer rescheduleProposedDuration,
    UUID scheduleProposedBy,
    boolean scheduleConfirmed
) {
    public static SessionDto from(LearningSession s) {
        return new SessionDto(
            s.getId(),
            s.getBarterRequest() == null ? null : s.getBarterRequest().getId(),
            s.getGroupId(),
            s.getFollowupOf() == null ? null : s.getFollowupOf().getId(),
            s.getBarterRequest() == null ? null : s.getBarterRequest().getRequester().getId(),
            s.getBarterRequest() == null ? null : s.getBarterRequest().getTargetUser().getId(),
            s.getScheduledAt(),
            s.getDurationMinutes(),
            s.getMeetingProvider().name(),
            s.isScheduleConfirmed() ? s.getMeetingRoom() : null,
            s.isScheduleConfirmed() ? s.getMeetingUrl() : null,
            s.getStatus().name(),
            s.getCompletedAt(),
            s.getRescheduleProposedBy() != null ? s.getRescheduleProposedBy().getId() : null,
            s.getRescheduleProposedAt(),
            s.getRescheduleProposedTime(),
            s.getRescheduleProposedDuration(),
            s.getScheduleProposedBy() == null ? null : s.getScheduleProposedBy().getId(),
            s.isScheduleConfirmed()
        );
    }
}
