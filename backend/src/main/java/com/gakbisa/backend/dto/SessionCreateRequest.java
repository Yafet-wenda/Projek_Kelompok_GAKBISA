package com.gakbisa.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record SessionCreateRequest(
    @NotNull UUID barterRequestId,
    @NotNull Instant scheduledAt,
    @Min(value = 15, message = "Durasi minimal 15 menit")
    @Max(value = 240, message = "Durasi maksimal 240 menit")
    int durationMinutes,
    String externalMeetingUrl
) {}
