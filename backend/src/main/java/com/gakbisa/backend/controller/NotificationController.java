package com.gakbisa.backend.controller;

import com.gakbisa.backend.domain.Notification;
import com.gakbisa.backend.service.NotificationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    static Map<String, Object> toMap(Notification n) {
        return Map.of(
            "id", n.getId(), "title", n.getTitle(), "body", n.getBody() == null ? "" : n.getBody(),
            "type", n.getType(), "read", n.isRead(), "createdAt", n.getCreatedAt());
    }

    @GetMapping
    public List<Map<String, Object>> inbox() {
        return notifications.inbox(CurrentUser.id()).stream().map(NotificationController::toMap).toList();
    }

    /** Untuk lencana di navbar. Respons: {"count": 3} */
    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("count", notifications.unreadCount(CurrentUser.id()));
    }

    @PostMapping("/{id}/read")
    public void markRead(@PathVariable UUID id) {
        notifications.markRead(CurrentUser.id(), id);
    }

    /** Tandai semua notifikasi sebagai sudah dibaca. Respons: {"updated": 3} */
    @PostMapping("/read-all")
    public Map<String, Integer> readAll() {
        return Map.of("updated", notifications.markAllRead(CurrentUser.id()));
    }
}
