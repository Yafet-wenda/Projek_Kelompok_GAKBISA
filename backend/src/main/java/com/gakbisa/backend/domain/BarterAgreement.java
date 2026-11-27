package com.gakbisa.backend.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "barter_agreements")
@Getter
@Setter
public class BarterAgreement {
    @Id
    @UuidGenerator
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "barter_request_id", nullable = false, unique = true)
    private BarterRequest barterRequest;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "session_count", nullable = false)
    private int sessionCount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proposed_by", nullable = false)
    private User proposedBy;

    @Column(name = "requester_accepted_at")
    private Instant requesterAcceptedAt;

    @Column(name = "target_accepted_at")
    private Instant targetAcceptedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean isAcceptedByBoth() {
        return requesterAcceptedAt != null && targetAcceptedAt != null;
    }

    @PrePersist
    void beforeInsert() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void beforeUpdate() {
        updatedAt = Instant.now();
    }
}
