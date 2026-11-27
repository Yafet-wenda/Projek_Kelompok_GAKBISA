package com.gakbisa.backend.controller;

import com.gakbisa.backend.config.AppExceptions.ValidationException;
import com.gakbisa.backend.domain.SessionStatus;
import com.gakbisa.backend.service.SessionService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/sessions/{sessionId}/feedback")
public class SessionFeedbackController {
    private final JdbcTemplate jdbc;
    private final SessionService sessions;

    public SessionFeedbackController(JdbcTemplate jdbc, SessionService sessions) {
        this.jdbc = jdbc;
        this.sessions = sessions;
    }

    @GetMapping
    public List<Map<String, Object>> list(@PathVariable UUID sessionId) {
        sessions.viewFor(CurrentUser.id(), sessionId);
        return jdbc.queryForList("""
            SELECT user_id AS "userId", achieved, remaining, needs_followup AS "needsFollowup", created_at AS "createdAt"
            FROM session_feedback WHERE learning_session_id = ? ORDER BY created_at
            """, sessionId);
    }

    @PostMapping
    public Map<String, Object> submit(@PathVariable UUID sessionId, @RequestBody FeedbackBody body) {
        var session = sessions.viewFor(CurrentUser.id(), sessionId);
        if (session.getStatus() != SessionStatus.COMPLETED) {
            throw new ValidationException("Umpan balik hanya dapat dikirim setelah sesi selesai");
        }
        String achieved = body.achieved() == null ? "" : body.achieved().trim();
        String remaining = body.remaining() == null ? "" : body.remaining().trim();
        if (achieved.isBlank()) throw new ValidationException("Ceritakan hal yang berhasil dicapai");
        if (achieved.length() > 2000 || remaining.length() > 2000) throw new ValidationException("Umpan balik maksimal 2000 karakter");
        if (jdbc.queryForObject("SELECT count(*) FROM session_feedback WHERE learning_session_id = ? AND user_id = ?", Integer.class, sessionId, CurrentUser.id()) > 0) {
            throw new ValidationException("Umpan balik untuk sesi ini sudah dikirim");
        }
        jdbc.update("INSERT INTO session_feedback (learning_session_id, user_id, achieved, remaining, needs_followup) VALUES (?, ?, ?, ?, ?)",
            sessionId, CurrentUser.id(), achieved, remaining, body.needsFollowup());
        return Map.of("saved", true);
    }

    public record FeedbackBody(String achieved, String remaining, boolean needsFollowup) {}
}
