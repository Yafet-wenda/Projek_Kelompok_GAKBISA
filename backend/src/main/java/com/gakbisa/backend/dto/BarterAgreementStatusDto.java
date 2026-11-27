package com.gakbisa.backend.dto;

import com.gakbisa.backend.domain.BarterAgreement;

import java.time.Instant;
import java.util.UUID;

public record BarterAgreementStatusDto(
    UUID barterRequestId,
    int durationMinutes,
    int sessionCount,
    UUID proposedBy,
    Instant requesterAcceptedAt,
    Instant targetAcceptedAt,
    boolean acceptedByBoth,
    Instant updatedAt
) {
    public static BarterAgreementStatusDto from(BarterAgreement agreement) {
        return new BarterAgreementStatusDto(
            agreement.getBarterRequest().getId(), agreement.getDurationMinutes(), agreement.getSessionCount(),
            agreement.getProposedBy().getId(), agreement.getRequesterAcceptedAt(), agreement.getTargetAcceptedAt(),
            agreement.isAcceptedByBoth(), agreement.getUpdatedAt());
    }
}
