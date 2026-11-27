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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "barter_request_id")
    private BarterRequest barterRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "followup_of_id")
    private LearningSession followupOf;

    @Column(name = "group_id", columnDefinition = "uuid")
    private UUID groupId;

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

    // ── Reschedule proposal ────────────────────────────────────────────────────
    /** ID user yang mengajukan penjadwalan ulang; null jika tidak ada proposal aktif. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reschedule_proposed_by")
    private com.gakbisa.backend.domain.User rescheduleProposedBy;

    /** Kapan proposal diajukan. */
    @Column(name = "reschedule_proposed_at")
    private Instant rescheduleProposedAt;

    /** Waktu baru yang diusulkan. */
    @Column(name = "reschedule_proposed_time")
    private Instant rescheduleProposedTime;

    /** Durasi baru yang diusulkan (menit); 0 = pakai durasi lama. */
    @Column(name = "reschedule_proposed_duration")
    private Integer rescheduleProposedDuration;

    @Column(name = "no_show_processed", nullable = false)
    private boolean noShowProcessed;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_proposed_by")
    private User scheduleProposedBy;

    @Column(name = "schedule_confirmed", nullable = false)
    private boolean scheduleConfirmed = true;
}
