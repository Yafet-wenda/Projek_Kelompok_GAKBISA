package com.gakbisa.backend.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "learning_sessions")
@Getter
@Setter
public class LearningSession {

    @Id
    @UuidGenerator
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "barter_request_id", nullable = false)
    private BarterRequest barterRequest;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "meeting_provider", nullable = false, length = 20)
    private MeetingProvider meetingProvider = MeetingProvider.JITSI;

    /** Nama ruang acak & unik — hindari nama yang mudah ditebak (proposal §8.3). */
    @Column(name = "meeting_room", nullable = false, unique = true, length = 255)
    private String meetingRoom;

    @Column(name = "meeting_url", columnDefinition = "text")
    private String meetingUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SessionStatus status = SessionStatus.SCHEDULED;

    @Column(name = "completed_at")
    private Instant completedAt;
}
