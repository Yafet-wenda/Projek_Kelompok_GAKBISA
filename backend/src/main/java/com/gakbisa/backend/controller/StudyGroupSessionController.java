package com.gakbisa.backend.controller;

import com.gakbisa.backend.config.AppExceptions.ForbiddenException;
import com.gakbisa.backend.config.AppExceptions.NotFoundException;
import com.gakbisa.backend.config.AppExceptions.ValidationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/groups/{groupId}/sessions")
public class StudyGroupSessionController {
    private final JdbcTemplate jdbc;
    public StudyGroupSessionController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping
    public List<Map<String, Object>> list(@PathVariable UUID groupId) {
        requireMember(groupId, CurrentUser.id());
        return jdbc.queryForList("SELECT id, scheduled_at AS \"scheduledAt\", duration_minutes AS \"durationMinutes\", meeting_room AS \"meetingRoom\", meeting_url AS \"meetingUrl\", status FROM learning_sessions WHERE group_id=? ORDER BY scheduled_at DESC", groupId);
    }

    @PostMapping
    @Transactional
    public Map<String, Object> schedule(@PathVariable UUID groupId, @RequestBody ScheduleBody body) {
        UUID actor = CurrentUser.id();
        if (!"OWNER".equals(requireMember(groupId, actor))) throw new ForbiddenException("Hanya pemilik kelompok yang dapat menjadwalkan sesi");
        if (body.scheduledAt() == null || body.scheduledAt().isBefore(Instant.now())) throw new ValidationException("Jadwal sesi harus berada di masa depan");
        if (body.durationMinutes() < 15 || body.durationMinutes() > 240) throw new ValidationException("Durasi harus 15 sampai 240 menit");
        String room = "gakbisa-group-" + UUID.randomUUID().toString().replace("-", "");
        UUID id = jdbc.queryForObject("INSERT INTO learning_sessions(group_id,scheduled_at,duration_minutes,meeting_provider,meeting_room,meeting_url,status) VALUES (?,?,?,'JITSI',?,?, 'SCHEDULED') RETURNING id",
            UUID.class, groupId, body.scheduledAt(), body.durationMinutes(), room, "https://meet.jit.si/" + room);
        jdbc.update("INSERT INTO notifications(user_id,title,body,type) SELECT user_id,'Sesi kelompok dijadwalkan',?,'SESSION' FROM group_members WHERE group_id=? AND status='ACTIVE' AND user_id<>?",
            "Sesi kelompok dijadwalkan pada " + body.scheduledAt(), groupId, actor);
        return jdbc.queryForMap("SELECT id, scheduled_at AS \"scheduledAt\", duration_minutes AS \"durationMinutes\", meeting_room AS \"meetingRoom\", meeting_url AS \"meetingUrl\", status FROM learning_sessions WHERE id=?", id);
    }

    @PostMapping("/{sessionId}/status")
    public Map<String, Object> status(@PathVariable UUID groupId, @PathVariable UUID sessionId, @RequestBody StatusBody body) {
        String role = requireMember(groupId, CurrentUser.id());
        if (!"OWNER".equals(role)) throw new ForbiddenException("Hanya pemilik kelompok yang dapat mengubah status sesi kelompok");
        if (!List.of("COMPLETED", "CANCELLED").contains(body.status())) throw new ValidationException("Status sesi tidak valid");
        int changed = jdbc.update("UPDATE learning_sessions SET status=?, completed_at=CASE WHEN ?='COMPLETED' THEN now() ELSE completed_at END WHERE id=? AND group_id=? AND status='SCHEDULED'", body.status(), body.status(), sessionId, groupId);
        if (changed == 0) throw new NotFoundException("Sesi tidak ditemukan atau statusnya sudah berubah");
        return Map.of("id", sessionId, "status", body.status());
    }

    private String requireMember(UUID groupId, UUID userId) {
        String state = jdbc.query("SELECT status FROM study_groups WHERE id=?", rs -> rs.next() ? rs.getString(1) : null, groupId);
        if (!"ACTIVE".equals(state)) throw new NotFoundException("Kelompok tidak ditemukan atau sudah ditutup");
        String role = jdbc.query("SELECT role FROM group_members WHERE group_id=? AND user_id=? AND status='ACTIVE'", rs -> rs.next() ? rs.getString(1) : null, groupId, userId);
        if (role == null) throw new ForbiddenException("Hanya anggota kelompok aktif yang dapat mengakses sesi");
        return role;
    }
    public record ScheduleBody(Instant scheduledAt, int durationMinutes) {}
    public record StatusBody(String status) {}
}
