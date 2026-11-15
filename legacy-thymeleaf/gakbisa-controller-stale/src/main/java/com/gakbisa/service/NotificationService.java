package com.gakbisa.service;

import com.gakbisa.dto.Views.NotificationView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface NotificationService {
    Page<NotificationView> list(String email, Pageable pageable);
    long unreadCount(String email);
    void markRead(String email, UUID notificationId);
    void markAllRead(String email);
}
