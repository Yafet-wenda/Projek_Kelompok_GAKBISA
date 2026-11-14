package com.gakbisa.backend.service;

import com.gakbisa.backend.config.AppExceptions.ForbiddenException;
import com.gakbisa.backend.config.AppExceptions.NotFoundException;
import com.gakbisa.backend.domain.Notification;
import com.gakbisa.backend.domain.User;
import com.gakbisa.backend.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {

    public static final String TYPE_BARTER = "BARTER";
    public static final String TYPE_SESSION = "SESSION";
    public static final String TYPE_REVIEW = "REVIEW";
    public static final String TYPE_MODERATION = "MODERATION";

    private final NotificationRepository notifications;

    public NotificationService(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    @Transactional
    public void push(User recipient, String type, String title, String body) {
        Notification n = new Notification();
        n.setUser(recipient);
        n.setType(type);
        n.setTitle(title);
        n.setBody(body);
        notifications.save(n);
    }

    @Transactional(readOnly = true)
    public List<Notification> inbox(UUID userId) {
        return notifications.findTop50ByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public void markRead(UUID userId, UUID notificationId) {
        Notification n = notifications.findById(notificationId)
            .orElseThrow(() -> new NotFoundException("Notifikasi tidak ditemukan"));
        if (!n.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Notifikasi bukan milik Anda");
        }
        n.setRead(true);
        notifications.save(n);
    }
}
