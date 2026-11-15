package com.gakbisa.controller;

import com.gakbisa.service.NotificationService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

@Controller
public class NotificationController extends BaseController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/notifications")
    public String list(@RequestParam(defaultValue = "0") int page, Principal principal, Model model) {
        model.addAttribute("notifications",
                notificationService.list(principal.getName(), PageRequest.of(safePage(page), PAGE_SIZE)));
        return "notification/list";
    }

    /** Untuk polling badge di navbar via JavaScript. */
    @GetMapping("/notifications/unread-count")
    @ResponseBody
    public Map<String, Long> unreadCount(Principal principal) {
        return Map.of("count", notificationService.unreadCount(principal.getName()));
    }

    @PostMapping("/notifications/{id}/read")
    public String markRead(@PathVariable UUID id, Principal principal) {
        notificationService.markRead(principal.getName(), id);
        return "redirect:/notifications";
    }

    @PostMapping("/notifications/read-all")
    public String markAllRead(Principal principal) {
        notificationService.markAllRead(principal.getName());
        return "redirect:/notifications";
    }
}
