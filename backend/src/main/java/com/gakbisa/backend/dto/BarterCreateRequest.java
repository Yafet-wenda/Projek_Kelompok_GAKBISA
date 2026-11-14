package com.gakbisa.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record BarterCreateRequest(
    @NotNull UUID targetUserId,
    @NotNull UUID offeredUserSkillId,
    @NotNull UUID wantedUserSkillId,
    @Size(max = 2000) String message
) {}
