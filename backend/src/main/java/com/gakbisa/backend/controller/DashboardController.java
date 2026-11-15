package com.gakbisa.backend.controller;

import com.gakbisa.backend.domain.BarterStatus;
import com.gakbisa.backend.domain.SessionStatus;
import com.gakbisa.backend.service.BarterService;
import com.gakbisa.backend.service.MatchingService;
import com.gakbisa.backend.service.NotificationService;
import com.gakbisa.backend.service.SessionService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Ringkasan beranda dalam satu panggilan (menggantikan model halaman /dashboard lama). */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final BarterService barter;
    private final SessionService sessions;
    private final NotificationService notifications;
    private final MatchingService matching;

    public DashboardController(BarterService barter, SessionService sessions,
                               NotificationService notifications, MatchingService matching) {
        this.barter = barter;
        this.sessions = sessions;
        this.notifications = notifications;
        this.matching = matching;
    }

    // Satu transaksi read-only untuk semua pemanggilan service di bawah, agar tidak ada
    // BEGIN/COMMIT terpisah (masing-masing satu bolak-balik ke database) per service.
    @Transactional(readOnly = true)
    @GetMapping
    public Map<String, Object> summary() {
        UUID uid = CurrentUser.id();
        long pendingBarters = barter.inbox(uid).stream()
            .filter(b -> b.getStatus() == BarterStatus.PENDING).count();
        long activeSessions = sessions.forUser(uid).stream()
            .filter(s -> s.getStatus() == SessionStatus.SCHEDULED || s.getStatus() == SessionStatus.ONGOING)
            .count();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("pendingBarters", pendingBarters);
        out.put("activeSessions", activeSessions);
        out.put("unreadNotifications", notifications.unreadCount(uid));
        out.put("topMatches", matching.recommend(uid, 4));
        out.put("recentNotifications", notifications.inbox(uid).stream()
            .limit(5).map(NotificationController::toMap).toList());
        return out;
    }
}