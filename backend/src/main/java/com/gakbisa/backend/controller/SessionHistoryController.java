package com.gakbisa.backend.controller;

import com.gakbisa.backend.service.SessionService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/sessions/{sessionId}/history")
public class SessionHistoryController {
    private final SessionService sessions;
    private final JdbcTemplate jdbc;
    public SessionHistoryController(SessionService sessions, JdbcTemplate jdbc) { this.sessions = sessions; this.jdbc = jdbc; }
    @GetMapping
    public List<Map<String, Object>> history(@PathVariable UUID sessionId) {
        sessions.viewFor(CurrentUser.id(), sessionId);
        return jdbc.queryForList("SELECT h.actor_id AS \"actorId\",u.full_name AS \"actorName\",h.old_scheduled_at AS \"oldScheduledAt\",h.proposed_scheduled_at AS \"proposedScheduledAt\",h.old_duration_minutes AS \"oldDurationMinutes\",h.proposed_duration_minutes AS \"proposedDurationMinutes\",h.decision,h.created_at AS \"createdAt\" FROM session_schedule_history h JOIN users u ON u.id=h.actor_id WHERE h.learning_session_id=? ORDER BY h.created_at DESC", sessionId);
    }
}
