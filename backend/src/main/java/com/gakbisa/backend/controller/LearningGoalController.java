package com.gakbisa.backend.controller;

import com.gakbisa.backend.config.AppExceptions.NotFoundException;
import com.gakbisa.backend.config.AppExceptions.ValidationException;
import com.gakbisa.backend.domain.BarterStatus;
import com.gakbisa.backend.service.BarterService;
import com.gakbisa.backend.service.NotificationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/barter/{barterId}/goals")
public class LearningGoalController {
    private final JdbcTemplate jdbc;
    private final BarterService barters;
    private final NotificationService notifications;
    public LearningGoalController(JdbcTemplate jdbc, BarterService barters, NotificationService notifications) { this.jdbc = jdbc; this.barters = barters; this.notifications = notifications; }

    @GetMapping
    public List<Map<String, Object>> list(@PathVariable UUID barterId) {
        barters.viewFor(CurrentUser.id(), barterId);
        return jdbc.queryForList("SELECT id, description, is_done AS \"isDone\", created_by AS \"createdBy\", created_at AS \"createdAt\" FROM learning_goals WHERE barter_request_id=? ORDER BY created_at", barterId);
    }

    @PostMapping
    @Transactional
    public Map<String, Object> add(@PathVariable UUID barterId, @RequestBody GoalBody body) {
        var barter = barters.viewFor(CurrentUser.id(), barterId);
        if (barter.getStatus() != BarterStatus.ACCEPTED) throw new ValidationException("Target belajar hanya dapat ditambahkan pada barter yang diterima");
        String text = body.description() == null ? "" : body.description().trim();
        if (text.isBlank() || text.length() > 500) throw new ValidationException("Target wajib diisi (maks. 500 karakter)");
        UUID id = jdbc.queryForObject("INSERT INTO learning_goals(barter_request_id,description,created_by) VALUES (?,?,?) RETURNING id", UUID.class, barterId, text, CurrentUser.id());
        return jdbc.queryForMap("SELECT id, description, is_done AS \"isDone\", created_by AS \"createdBy\", created_at AS \"createdAt\" FROM learning_goals WHERE id=?", id);
    }

    @PatchMapping("/{goalId}")
    @Transactional
    public Map<String, Object> toggle(@PathVariable UUID barterId, @PathVariable UUID goalId, @RequestBody GoalUpdate body) {
        barters.viewFor(CurrentUser.id(), barterId);
        int changed = jdbc.update("UPDATE learning_goals SET is_done=? WHERE id=? AND barter_request_id=?", body.done(), goalId, barterId);
        if (changed == 0) throw new NotFoundException("Target belajar tidak ditemukan");
        int remaining = jdbc.queryForObject("SELECT count(*) FROM learning_goals WHERE barter_request_id=? AND is_done=FALSE", Integer.class, barterId);
        int total = jdbc.queryForObject("SELECT count(*) FROM learning_goals WHERE barter_request_id=?", Integer.class, barterId);
        if (total > 0 && remaining == 0 && barters.require(barterId).getStatus() == BarterStatus.ACCEPTED) {
            barters.complete(barterId);
            var barter = barters.require(barterId);
            notifications.push(barter.getRequester(), NotificationService.TYPE_BARTER, "Semua target selesai", "Barter ditutup karena semua target belajar telah dicapai.");
            notifications.push(barter.getTargetUser(), NotificationService.TYPE_BARTER, "Semua target selesai", "Barter ditutup karena semua target belajar telah dicapai.");
        }
        return Map.of("id", goalId, "isDone", body.done());
    }
    public record GoalBody(String description) {}
    public record GoalUpdate(boolean done) {}
}
