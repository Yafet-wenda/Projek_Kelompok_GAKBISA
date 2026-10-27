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

    @GetMapping
    public List<Map<String, Object>> inbox() {
        return notifications.inbox(CurrentUser.id()).stream()
            .map(n -> Map.<String, Object>of(
                "id", n.getId(), "title", n.getTitle(), "body", n.getBody() == null ? "" : n.getBody(),
                "type", n.getType(), "read", n.isRead(), "createdAt", n.getCreatedAt()))
            .toList();
    }

    @PostMapping("/{id}/read")
    public void markRead(@PathVariable UUID id) {
        notifications.markRead(CurrentUser.id(), id);
    }
}
