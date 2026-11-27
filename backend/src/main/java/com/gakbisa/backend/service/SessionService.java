package com.gakbisa.backend.service;

import com.gakbisa.backend.config.AppExceptions.ForbiddenException;
import com.gakbisa.backend.config.AppExceptions.NotFoundException;
import com.gakbisa.backend.config.AppExceptions.ValidationException;
import com.gakbisa.backend.domain.BarterRequest;
import com.gakbisa.backend.domain.BarterStatus;
import com.gakbisa.backend.domain.LearningSession;
import com.gakbisa.backend.domain.MeetingProvider;
import com.gakbisa.backend.domain.SessionStatus;
import com.gakbisa.backend.domain.User;
import com.gakbisa.backend.dto.SessionCreateRequest;
import com.gakbisa.backend.repository.BarterRequestRepository;
import com.gakbisa.backend.repository.LearningSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final NotificationService notifications;
    private JdbcTemplate jdbc;

    public SessionService(LearningSessionRepository sessions, BarterRequestRepository barter,
                          NotificationService notifications) {
        this.sessions = sessions;
        this.barter = barter;
        this.notifications = notifications;
    }

    @Autowired
    public void setJdbcTemplate(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public LearningSession schedule(UUID actorId, SessionCreateRequest req) {
        BarterRequest b = barter.findById(req.barterRequestId())
            .orElseThrow(() -> new NotFoundException("Permintaan barter tidak ditemukan"));
        // Skenario uji #7: sesi ditolak sebelum barter diterima.
        if (b.getStatus() != BarterStatus.ACCEPTED) {
            throw new ValidationException("Sesi hanya dapat dijadwalkan untuk barter berstatus ACCEPTED");
        }
        requireParticipant(b, actorId);
        if (jdbc != null) jdbc.queryForObject("SELECT id FROM barter_requests WHERE id = ? FOR UPDATE", UUID.class, b.getId());
        validateAgreement(b.getId(), req.durationMinutes());
        if (req.scheduledAt().isBefore(Instant.now())) {
            throw new ValidationException("Jadwal sesi tidak boleh di masa lalu");
        }

        LearningSession s = new LearningSession();
        s.setBarterRequest(b);
        s.setScheduledAt(req.scheduledAt());
        s.setDurationMinutes(req.durationMinutes());
        s.setStatus(SessionStatus.SCHEDULED);
        s.setScheduleProposedBy(b.getRequester().getId().equals(actorId) ? b.getRequester() : b.getTargetUser());
        s.setScheduleConfirmed(false);

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
        notifications.push(other, NotificationService.TYPE_SESSION, "Usulan jadwal sesi",
            "Setujui atau tolak sesi untuk barter '" + b.getWantedUserSkill().getSkill().getName() + "' pada "
                + req.scheduledAt());
        return saved;
    }

    @Transactional
    public LearningSession confirmInitialSchedule(UUID actorId, UUID sessionId, boolean accept) {
        LearningSession s = require(sessionId);
        BarterRequest b = s.getBarterRequest();
        requireParticipant(b, actorId);
        if (s.isScheduleConfirmed() || s.getStatus() != SessionStatus.SCHEDULED) throw new ValidationException("Usulan jadwal sudah diproses atau tidak aktif");
        if (s.getScheduleProposedBy() != null && s.getScheduleProposedBy().getId().equals(actorId)) throw new ForbiddenException("Pengusul tidak dapat menyetujui jadwalnya sendiri");
        User proposer = s.getScheduleProposedBy();
        if (accept) s.setScheduleConfirmed(true);
        else s.setStatus(SessionStatus.CANCELLED);
        LearningSession saved = sessions.save(s);
        if (proposer != null) notifications.push(proposer, NotificationService.TYPE_SESSION,
            accept ? "Jadwal sesi diterima" : "Jadwal sesi ditolak",
            accept ? "Partner menyetujui jadwal sesi." : "Partner menolak jadwal yang diusulkan.");
        return saved;
    }

    @Transactional
    public LearningSession reschedule(UUID actorId, UUID sessionId, Instant newTime, int durationMinutes) {
        LearningSession s = require(sessionId);
        requireParticipant(s.getBarterRequest(), actorId);
        if (s.getStatus() != SessionStatus.SCHEDULED && s.getStatus() != SessionStatus.PENDING_RESCHEDULE) {
            throw new ValidationException("Hanya sesi SCHEDULED atau PENDING_RESCHEDULE yang dapat dijadwal ulang");
        }
        if (newTime.isBefore(Instant.now())) {
            throw new ValidationException("Jadwal baru tidak boleh di masa lalu");
        }
        int proposedDuration = durationMinutes > 0 ? durationMinutes : s.getDurationMinutes();

        // Simpan proposal — jangan langsung ubah scheduledAt
        com.gakbisa.backend.domain.User actor = s.getBarterRequest().getRequester().getId().equals(actorId)
            ? s.getBarterRequest().getRequester()
            : s.getBarterRequest().getTargetUser();
        s.setRescheduleProposedBy(actor);
        s.setRescheduleProposedAt(Instant.now());
        s.setRescheduleProposedTime(newTime);
        s.setRescheduleProposedDuration(durationMinutes > 0 ? durationMinutes : null);
        recordHistory(sessionId, actorId, s.getScheduledAt(), newTime, s.getDurationMinutes(), proposedDuration, "PROPOSED");
        s.setStatus(SessionStatus.PENDING_RESCHEDULE);
        LearningSession saved = sessions.save(s);

        // Notifikasi ke partner
        var partner = s.getBarterRequest().getRequester().getId().equals(actorId)
            ? s.getBarterRequest().getTargetUser()
            : s.getBarterRequest().getRequester();
        notifications.push(partner, NotificationService.TYPE_SESSION,
            "Usulan penjadwalan ulang",
            actor.getFullName() + " mengusulkan jadwal baru untuk sesi ini. Silakan konfirmasi atau tolak.");
        return saved;
    }

    /**
     * Pihak yang BUKAN pengusul menerima proposal reschedule.
     * Jadwal sesi langsung diperbarui dan status kembali ke SCHEDULED.
     */
    @Transactional
    public LearningSession confirmReschedule(UUID actorId, UUID sessionId) {
        LearningSession s = require(sessionId);
        requireParticipant(s.getBarterRequest(), actorId);
        if (s.getStatus() != SessionStatus.PENDING_RESCHEDULE) {
            throw new ValidationException("Tidak ada proposal penjadwalan ulang yang aktif");
        }
        if (s.getRescheduleProposedBy() != null && s.getRescheduleProposedBy().getId().equals(actorId)) {
            throw new ForbiddenException("Anda tidak dapat menerima proposal yang Anda ajukan sendiri");
        }

        var proposer = s.getRescheduleProposedBy();
        recordHistory(sessionId, actorId, s.getScheduledAt(), s.getRescheduleProposedTime(), s.getDurationMinutes(),
            s.getRescheduleProposedDuration() == null ? s.getDurationMinutes() : s.getRescheduleProposedDuration(), "ACCEPTED");
        s.setScheduledAt(s.getRescheduleProposedTime());
        if (s.getRescheduleProposedDuration() != null && s.getRescheduleProposedDuration() > 0) {
            s.setDurationMinutes(s.getRescheduleProposedDuration());
        }
        clearRescheduleProposal(s);
        s.setStatus(SessionStatus.SCHEDULED);
        LearningSession saved = sessions.save(s);

        // Notifikasi ke pengusul
        if (proposer != null) {
            notifications.push(proposer, NotificationService.TYPE_SESSION,
                "Jadwal baru diterima",
                "Partner menerima jadwal baru untuk sesi ini.");
        }
        return saved;
    }

    /**
     * Siapapun peserta dapat menolak proposal (pengusul menarik kembali,
     * penerima menolak). Status kembali ke SCHEDULED tanpa mengubah jadwal.
     */
    @Transactional
    public LearningSession rejectReschedule(UUID actorId, UUID sessionId) {
        LearningSession s = require(sessionId);
        requireParticipant(s.getBarterRequest(), actorId);
        if (s.getStatus() != SessionStatus.PENDING_RESCHEDULE) {
            throw new ValidationException("Tidak ada proposal penjadwalan ulang yang aktif");
        }

        // Tentukan pihak lain untuk notifikasi sebelum di-clear
        UUID proposedById = s.getRescheduleProposedBy() != null ? s.getRescheduleProposedBy().getId() : null;
        recordHistory(sessionId, actorId, s.getScheduledAt(), s.getRescheduleProposedTime(), s.getDurationMinutes(),
            s.getRescheduleProposedDuration() == null ? s.getDurationMinutes() : s.getRescheduleProposedDuration(),
            actorId.equals(proposedById) ? "WITHDRAWN" : "REJECTED");
        clearRescheduleProposal(s);
        s.setStatus(SessionStatus.SCHEDULED);
        LearningSession saved = sessions.save(s);

        // Notifikasi ke pihak lain
        var partner = s.getBarterRequest().getRequester().getId().equals(actorId)
            ? s.getBarterRequest().getTargetUser()
            : s.getBarterRequest().getRequester();
        String msg = actorId.equals(proposedById)
            ? "Pengusul membatalkan usulan penjadwalan ulang. Jadwal tetap seperti semula."
            : "Partner menolak usulan penjadwalan ulang. Jadwal tetap seperti semula.";
        notifications.push(partner, NotificationService.TYPE_SESSION, "Usulan jadwal ditolak", msg);
        return saved;
    }

    @Transactional
    public LearningSession setStatus(UUID actorId, UUID sessionId, SessionStatus next) {
        LearningSession s = require(sessionId);
        requireParticipant(s.getBarterRequest(), actorId);

        switch (next) {
            case ONGOING -> {
                if (!s.isScheduleConfirmed()) throw new ValidationException("Jadwal sesi belum disetujui kedua pihak");
                if (s.getStatus() != SessionStatus.SCHEDULED) {
                    throw new ValidationException("Sesi harus SCHEDULED sebelum dimulai");
                }
                if (Duration.between(Instant.now(), s.getScheduledAt()).abs().toMinutes() > 60) {
                    throw new ValidationException("Meeting hanya dapat dibuka mendekati jadwal sesi");
                }
            }
            case COMPLETED -> {
                if (!s.isScheduleConfirmed()) {
                    throw new ValidationException("Jadwal sesi belum disetujui kedua pihak");
                }
                if (s.getStatus() != SessionStatus.ONGOING && s.getStatus() != SessionStatus.SCHEDULED) {
                    throw new ValidationException("Hanya sesi SCHEDULED/ONGOING yang dapat diselesaikan");
                }
                s.setCompletedAt(Instant.now());
            }
            case CANCELLED -> {
                if (s.getStatus() == SessionStatus.COMPLETED) {
                    throw new ValidationException("Sesi selesai tidak dapat dibatalkan");
                }
                clearRescheduleProposal(s);
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
        return saved;
    }

    @Transactional
    public LearningSession scheduleFollowup(UUID actorId, UUID completedSessionId, Instant scheduledAt, int durationMinutes) {
        LearningSession previous = viewFor(actorId, completedSessionId);
        if (previous.getStatus() != SessionStatus.COMPLETED || previous.getBarterRequest() == null) {
            throw new ValidationException("Sesi lanjutan hanya dapat dibuat dari sesi barter yang selesai");
        }
        if (scheduledAt == null) throw new ValidationException("Jadwal sesi wajib diisi");
        int openGoals = jdbc.queryForObject("SELECT count(*) FROM learning_goals WHERE barter_request_id=? AND is_done=FALSE", Integer.class, previous.getBarterRequest().getId());
        int followupRequested = jdbc.queryForObject("SELECT count(*) FROM session_feedback WHERE learning_session_id=? AND needs_followup=TRUE", Integer.class, completedSessionId);
        if (openGoals == 0 && followupRequested == 0) {
            throw new ValidationException("Semua target belajar sudah selesai; barter dapat ditutup oleh kedua pihak");
        }
        if (durationMinutes < 15 || durationMinutes > 240) throw new ValidationException("Durasi sesi harus 15 sampai 240 menit");
        if (scheduledAt.isBefore(Instant.now())) throw new ValidationException("Jadwal sesi tidak boleh di masa lalu");
        LearningSession next = schedule(actorId,
            new SessionCreateRequest(previous.getBarterRequest().getId(), scheduledAt, durationMinutes, null));
        next.setFollowupOf(previous);
        return sessions.save(next);
    }

    /** Akses meeting hanya untuk peserta sesi (kebutuhan fungsional proposal §4.5). */
    @Transactional(readOnly = true)
    public LearningSession viewFor(UUID actorId, UUID sessionId) {
        LearningSession s = require(sessionId);
        if (s.getBarterRequest() != null) requireParticipant(s.getBarterRequest(), actorId);
        else if (s.getGroupId() != null && jdbc.queryForObject(
            "SELECT count(*) FROM group_members WHERE group_id=? AND user_id=? AND status='ACTIVE'",
            Integer.class, s.getGroupId(), actorId) == 0) {
            throw new ForbiddenException("Hanya anggota aktif kelompok yang dapat membuka sesi ini");
        } else if (s.getGroupId() == null) throw new ForbiddenException("Sesi tidak memiliki peserta yang valid");
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

    private void requireParticipant(BarterRequest b, UUID actorId) {
        if (b == null) {
            throw new ForbiddenException("Sesi tidak memiliki barter peserta");
        }
        if (!b.getRequester().getId().equals(actorId) && !b.getTargetUser().getId().equals(actorId)) {
            throw new ForbiddenException("Anda bukan peserta sesi ini");
        }
    }

    private void validateAgreement(UUID barterId, int durationMinutes) {
        // Spring injects JDBC in production. Null is retained only for the focused unit-test constructor.
        if (jdbc == null) return;
        var agreement = jdbc.query("""
            SELECT duration_minutes, session_count,
                   requester_accepted_at IS NOT NULL AND target_accepted_at IS NOT NULL AS accepted
            FROM barter_agreements WHERE barter_request_id = ?
            """, rs -> rs.next() ? new Object[] {rs.getInt("duration_minutes"), rs.getInt("session_count"), rs.getBoolean("accepted")} : null,
            barterId);
        if (agreement == null || !Boolean.TRUE.equals(agreement[2])) {
            throw new ValidationException("Kedua pihak harus menyetujui kesepakatan barter sebelum menjadwalkan sesi");
        }
        if ((Integer) agreement[0] != durationMinutes) {
            throw new ValidationException("Durasi sesi harus mengikuti kesepakatan: " + agreement[0] + " menit");
        }
        Integer scheduled = jdbc.queryForObject("SELECT count(*) FROM learning_sessions WHERE barter_request_id = ? AND status <> 'CANCELLED'",
            Integer.class, barterId);
        if (scheduled != null && scheduled >= (Integer) agreement[1]) {
            throw new ValidationException("Jumlah sesi yang disepakati sudah tercapai");
        }
    }

    private void clearRescheduleProposal(LearningSession s) {
        s.setRescheduleProposedBy(null);
        s.setRescheduleProposedAt(null);
        s.setRescheduleProposedTime(null);
        s.setRescheduleProposedDuration(null);
    }

    private void recordHistory(UUID sessionId, UUID actorId, Instant oldTime, Instant proposedTime,
                               int oldDuration, int proposedDuration, String decision) {
        if (jdbc == null) return; // Constructor kept test-friendly; Spring injects JDBC in the running app.
        jdbc.update("INSERT INTO session_schedule_history(learning_session_id,actor_id,old_scheduled_at,proposed_scheduled_at,old_duration_minutes,proposed_duration_minutes,decision) VALUES (?,?,?,?,?,?,?)",
            sessionId, actorId, oldTime, proposedTime, oldDuration, proposedDuration, decision);
    }

    private static String randomToken(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) sb.append(ROOM_ALPHABET[RANDOM.nextInt(ROOM_ALPHABET.length)]);
        return sb.toString();
    }
}
