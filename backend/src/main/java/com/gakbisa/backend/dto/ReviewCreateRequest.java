package com.gakbisa.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ReviewCreateRequest(
    @NotNull UUID learningSessionId,
    @Min(1) @Max(5) short rating,
    @Size(max = 2000) String comment
) {}
