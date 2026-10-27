package com.gakbisa.backend.domain;

import java.util.Set;

public enum BarterStatus {
    PENDING, ACCEPTED, REJECTED, CANCELLED, COMPLETED;

    /** Transisi status yang sah (state machine barter). */
    public boolean canTransitionTo(BarterStatus next) {
        return ALLOWED.get(this).contains(next);
    }

    private static final java.util.Map<BarterStatus, Set<BarterStatus>> ALLOWED = java.util.Map.of(
        PENDING, Set.of(ACCEPTED, REJECTED, CANCELLED),
        ACCEPTED, Set.of(COMPLETED, CANCELLED),
        REJECTED, Set.of(),
        CANCELLED, Set.of(),
        COMPLETED, Set.of()
    );
}
