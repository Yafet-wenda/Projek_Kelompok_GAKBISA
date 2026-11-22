package com.gakbisa.backend.service;

import com.gakbisa.backend.config.AppExceptions.ForbiddenException;
import com.gakbisa.backend.config.AppExceptions.NotFoundException;
import com.gakbisa.backend.config.AppExceptions.ValidationException;
import com.gakbisa.backend.domain.BarterRequest;
import com.gakbisa.backend.domain.BarterStatus;
import com.gakbisa.backend.domain.LearningSession;
import com.gakbisa.backend.domain.MeetingProvider;
import com.gakbisa.backend.domain.SessionStatus;
import com.gakbisa.backend.dto.SessionCreateRequest;
import com.gakbisa.backend.repository.BarterRequestRepository;
import com.gakbisa.backend.repository.LearningSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Penjadwalan sesi belajar dari barter yang DITERIMA, plus pembuatan ruang Jitsi
 * dengan ID acak anti-tebak (mitigasi risiko proposal §8.3).
 */
@Service
public class SessionService {

    private static final String JITSI_BASE = "https://meet.jit.si/";
    private static final char[] ROOM_ALPHABET =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final LearningSessionRepository sessions;
    private final BarterRequestRepository barter;
    private final BarterService barterService;
    private final NotificationService notifications;

    public SessionService(LearningSessionRepository sessions, BarterRequestRepository barter,
                          BarterService barterService, NotificationService notifications) {
        this.sessions = sessions;
        this.barter = barter;
        this.barterService = barterService;
        this.notifications = notifications;
    }

    @Transactional
    public LearningSession schedule(UUID actorId, SessionCreateRequest req) {
        BarterRequest b = barter.findByIdForUpdate(req.barterRequestId())
            .orElseThrow(() -> new NotFoundException("Permintaan barter tidak ditemukan"));
        // Skenario uji #7: sesi ditolak sebelum barter diterima.
        if (b.getStatus() != BarterStatus.ACCEPTED) {
            throw new ValidationException("Sesi hanya dapat dijadwalkan untuk barter berstatus ACCEPTED");
        }
        requireParticipant(b, actorId);
        if (req.scheduledAt().isBefore(Instant.now())) {
            throw new ValidationException("Jadwal sesi tidak boleh di masa lalu");
        }

        LearningSession s = new LearningSession();
        s.setBarterRequest(b);
        s.setScheduledAt(req.scheduledAt());
        s.setDurationMinutes(req.durationMinutes());
        s.setStatus(SessionStatus.SCHEDULED);

        if (req.externalMeetingUrl() != null && !req.externalMeetingUrl().isBlank()) {
            s.setMeetingProvider(MeetingProvider.EXTERNAL_LINK);
            s.setMeetingRoom("ext-" + randomToken(16));
            s.setMeetingUrl(req.externalMeetingUrl().trim());
        } else {
            s.setMeetingProvider(MeetingProvider.JITSI);
            String room = "gakbisa-" + randomToken(20);
            s.setMeetingRoom(room);
            s.setMeetingUrl(JITSI_BASE + room);
        }
        LearningSession saved = sessions.save(s);

        var other = b.getRequester().getId().equals(actorId) ? b.getTargetUser() : b.getRequester();
        notifications.push(other, NotificationService.TYPE_SESSION, "Sesi belajar dijadwalkan",
            "Sesi untuk barter '" + b.getWantedUserSkill().getSkill().getName() + "' dijadwalkan pada "
                + req.scheduledAt());
        return saved;
    }

    @Transactional
    public LearningSession reschedule(UUID actorId, UUID sessionId, Instant newTime, int durationMinutes) {
        LearningSession s = requireForUpdate(sessionId);
        requireParticipant(s.getBarterRequest(), actorId);
        requireAccepted(s.getBarterRequest());
        if (s.getStatus() != SessionStatus.SCHEDULED) {
            throw new ValidationException("Hanya sesi SCHEDULED yang dapat dijadwal ulang");
        }
        if (newTime.isBefore(Instant.now())) {
            throw new ValidationException("Jadwal baru tidak boleh di masa lalu");
        }
        s.setScheduledAt(newTime);
        if (durationMinutes > 0) s.setDurationMinutes(durationMinutes);
        return sessions.save(s);
    }

    @Transactional
    public LearningSession setStatus(UUID actorId, UUID sessionId, SessionStatus next) {
        LearningSession s = requireForUpdate(sessionId);
        requireParticipant(s.getBarterRequest(), actorId);
        if (next == SessionStatus.ONGOING || next == SessionStatus.COMPLETED) {
            requireAccepted(s.getBarterRequest());
        }

        switch (next) {
            case ONGOING -> {
                if (s.getStatus() != SessionStatus.SCHEDULED) {
                    throw new ValidationException("Sesi harus SCHEDULED sebelum dimulai");
                }
                if (Duration.between(Instant.now(), s.getScheduledAt()).abs().toMinutes() > 60) {
                    throw new ValidationException("Meeting hanya dapat dibuka mendekati jadwal sesi");
                }
            }
            case COMPLETED -> {
                if (s.getStatus() != SessionStatus.ONGOING && s.getStatus() != SessionStatus.SCHEDULED) {
                    throw new ValidationException("Hanya sesi SCHEDULED/ONGOING yang dapat diselesaikan");
                }
                s.setCompletedAt(Instant.now());
            }
            case CANCELLED -> {
                if (s.getStatus() == SessionStatus.COMPLETED) {
                    throw new ValidationException("Sesi selesai tidak dapat dibatalkan");
                }
            }
            default -> throw new ValidationException("Transisi ke " + next + " tidak didukung");
        }
        s.setStatus(next);
        LearningSession saved = sessions.save(s);

        if (next == SessionStatus.COMPLETED) {
            var b = s.getBarterRequest();
            notifications.push(b.getRequester(), NotificationService.TYPE_SESSION, "Sesi selesai",
                "Tandai ulasan untuk sesi ini bila belum memberi.");
            notifications.push(b.getTargetUser(), NotificationService.TYPE_SESSION, "Sesi selesai",
                "Tandai ulasan untuk sesi ini bila belum memberi.");
        }
        if (next == SessionStatus.COMPLETED || next == SessionStatus.CANCELLED) {
            var b = s.getBarterRequest();
            if (b.getStatus() == BarterStatus.ACCEPTED) {
                List<LearningSession> children = sessions.findByBarterRequestId(b.getId());
                boolean active = children.stream()
                    .anyMatch(x -> x.getStatus() == SessionStatus.SCHEDULED || x.getStatus() == SessionStatus.ONGOING);
                boolean completed = children.stream().anyMatch(x -> x.getStatus() == SessionStatus.COMPLETED);
                if (!active && completed) {
                    barterService.complete(b.getId());
                }
            }
        }
        return saved;
    }

    /** Akses meeting hanya untuk peserta sesi (kebutuhan fungsional proposal §4.5). */
    @Transactional(readOnly = true)
    public LearningSession viewFor(UUID actorId, UUID sessionId) {
        LearningSession s = require(sessionId);
        requireParticipant(s.getBarterRequest(), actorId);
        return s;
    }

    @Transactional(readOnly = true)
    public List<LearningSession> forUser(UUID userId) {
        return sessions.findForParticipant(userId);
    }

    @Transactional(readOnly = true)
    public LearningSession require(UUID sessionId) {
        return sessions.findById(sessionId).orElseThrow(() -> new NotFoundException("Sesi tidak ditemukan"));
    }

    private LearningSession requireForUpdate(UUID sessionId) {
        UUID barterId = sessions.findBarterRequestIdById(sessionId)
            .orElseThrow(() -> new NotFoundException("Sesi tidak ditemukan"));
        barter.findByIdForUpdate(barterId)
            .orElseThrow(() -> new NotFoundException("Permintaan barter tidak ditemukan"));
        return require(sessionId);
    }

    private void requireAccepted(BarterRequest b) {
        if (b.getStatus() != BarterStatus.ACCEPTED) {
            throw new ValidationException("Sesi hanya dapat diubah untuk barter berstatus ACCEPTED");
        }
    }

    private void requireParticipant(BarterRequest b, UUID actorId) {
        if (!b.getRequester().getId().equals(actorId) && !b.getTargetUser().getId().equals(actorId)) {
            throw new ForbiddenException("Anda bukan peserta sesi ini");
        }
    }

    private static String randomToken(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) sb.append(ROOM_ALPHABET[RANDOM.nextInt(ROOM_ALPHABET.length)]);
        return sb.toString();
    }
}
