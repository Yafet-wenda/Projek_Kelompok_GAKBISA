package com.gakbisa.backend.controller;

import com.gakbisa.backend.config.AppExceptions.ValidationException;
import com.gakbisa.backend.domain.SessionStatus;
import com.gakbisa.backend.service.SessionService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/sessions/{sessionId}/attendance")
public class SessionAttendanceController {
    private final JdbcTemplate jdbc;
    private final SessionService sessions;
    public SessionAttendanceController(JdbcTemplate jdbc, SessionService sessions) { this.jdbc = jdbc; this.sessions = sessions; }

    @GetMapping
    public Map<String, Object> mine(@PathVariable UUID sessionId) {
        sessions.viewFor(CurrentUser.id(), sessionId);
        Boolean present = jdbc.query("SELECT attended FROM session_attendance WHERE learning_session_id=? AND user_id=?", rs -> rs.next() ? rs.getBoolean(1) : null, sessionId, CurrentUser.id());
        return Map.of("attended", Boolean.TRUE.equals(present));
    }

    @PostMapping
    public Map<String, Object> checkIn(@PathVariable UUID sessionId) {
        var session = sessions.viewFor(CurrentUser.id(), sessionId);
        if (session.getBarterRequest() == null) throw new ValidationException("Konfirmasi kehadiran otomatis hanya tersedia untuk sesi barter dua orang");
        if (session.getStatus() == SessionStatus.CANCELLED || session.isNoShowProcessed()) throw new ValidationException("Konfirmasi kehadiran untuk sesi ini sudah ditutup");
        Instant endsAt = session.getScheduledAt().plusSeconds(session.getDurationMinutes() * 60L);
        if (Instant.now().isBefore(endsAt.minusSeconds(15 * 60L))) throw new ValidationException("Konfirmasi kehadiran tersedia mendekati akhir sesi");
        jdbc.update("INSERT INTO session_attendance(learning_session_id,user_id,attended) VALUES (?,?,TRUE) ON CONFLICT(learning_session_id,user_id) DO UPDATE SET attended=TRUE,marked_at=now()", sessionId, CurrentUser.id());
        return Map.of("attended", true);
    }
}
