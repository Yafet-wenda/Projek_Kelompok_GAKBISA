package com.gakbisa.backend.controller;

import com.gakbisa.backend.dto.UserDto;
import com.gakbisa.backend.security.GakbisaUserDetails;
import com.gakbisa.backend.service.JitsiMeetingService;
import com.gakbisa.backend.service.NotificationService;
import com.gakbisa.backend.service.PortfolioService;
import com.gakbisa.backend.service.UserService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class WebDataController {
    private final UserService users;
    private final NotificationService notifications;
    private final JitsiMeetingService meetings;
    private final PortfolioService portfolios;

    public WebDataController(UserService users, NotificationService notifications,
                             JitsiMeetingService meetings, PortfolioService portfolios) {
        this.users = users;
        this.notifications = notifications;
        this.meetings = meetings;
        this.portfolios = portfolios;
    }

    @GetMapping("/web/api/me")
    public UserDto me() {
        return UserDto.from(users.require(CurrentUser.id()));
    }

    @GetMapping("/web/api/notifications")
    public List<Map<String, Object>> notifications() {
        return notifications.inbox(CurrentUser.id()).stream().map(NotificationController::toMap).toList();
    }

    @GetMapping("/web/api/sessions/{id}/meeting")
    public ResponseEntity<JitsiMeetingService.MeetingAccess> meeting(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(meetings.accessFor(CurrentUser.id(), id));
    }

    @GetMapping("/web/portfolios/{id}/content")
    public ResponseEntity<Resource> content(@PathVariable UUID id, Authentication authentication) {
        UUID viewerId = authentication != null && authentication.getPrincipal() instanceof GakbisaUserDetails details
            ? details.getUser().getId() : null;
        var file = portfolios.content(viewerId, id);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .contentType(MediaType.parseMediaType(file.contentType()))
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
            .body(new ByteArrayResource(file.bytes()));
    }
}
