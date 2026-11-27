package com.gakbisa.backend.controller;

import com.gakbisa.backend.config.AppExceptions.ValidationException;
import com.gakbisa.backend.domain.BarterStatus;
import com.gakbisa.backend.service.BarterService;
import com.gakbisa.backend.service.NotificationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/barter/{barterId}/close")
public class BarterCompletionController {
    private final JdbcTemplate jdbc;
    private final BarterService barters;
    private final NotificationService notifications;
    public BarterCompletionController(JdbcTemplate jdbc, BarterService barters, NotificationService notifications) {
        this.jdbc = jdbc; this.barters = barters; this.notifications = notifications;
    }

    @PostMapping
    @Transactional
    public Map<String, Object> confirmClose(@PathVariable UUID barterId) {
        var b = barters.viewFor(CurrentUser.id(), barterId);
        if (b.getStatus() != BarterStatus.ACCEPTED) throw new ValidationException("Barter ini tidak lagi aktif");
        jdbc.queryForObject("SELECT id FROM barter_requests WHERE id=? FOR UPDATE", UUID.class, barterId);
        int inserted = jdbc.update("INSERT INTO barter_close_confirmations(barter_request_id,user_id) VALUES (?,?) ON CONFLICT DO NOTHING", barterId, CurrentUser.id());
        int count = jdbc.queryForObject("SELECT count(*) FROM barter_close_confirmations WHERE barter_request_id=?", Integer.class, barterId);
        if (count >= 2) {
            barters.complete(barterId);
            notifications.push(b.getRequester(), NotificationService.TYPE_BARTER, "Barter selesai", "Kedua pihak menyetujui penutupan barter.");
            notifications.push(b.getTargetUser(), NotificationService.TYPE_BARTER, "Barter selesai", "Kedua pihak menyetujui penutupan barter.");
        } else if (inserted > 0) {
            var partner = b.getRequester().getId().equals(CurrentUser.id()) ? b.getTargetUser() : b.getRequester();
            notifications.push(partner, NotificationService.TYPE_BARTER, "Konfirmasi penutupan barter", "Partner ingin menutup barter. Konfirmasi dari halaman barter.");
        }
        return Map.of("confirmations", count, "status", count >= 2 ? "COMPLETED" : "ACCEPTED");
    }
}
