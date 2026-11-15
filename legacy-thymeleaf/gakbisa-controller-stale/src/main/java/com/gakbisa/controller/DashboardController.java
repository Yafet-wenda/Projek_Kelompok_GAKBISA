package com.gakbisa.controller;

import com.gakbisa.service.BarterService;
import com.gakbisa.service.MatchingService;
import com.gakbisa.service.SessionService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController extends BaseController {

    private final MatchingService matchingService;
    private final BarterService barterService;
    private final SessionService sessionService;

    public DashboardController(MatchingService matchingService, BarterService barterService,
                               SessionService sessionService) {
        this.matchingService = matchingService;
        this.barterService = barterService;
        this.sessionService = sessionService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) {
            return "redirect:/admin";
        }
        String email = authentication.getName();
        model.addAttribute("recommendations", matchingService.recommend(email, 5));
        model.addAttribute("incomingRequests", barterService.incoming(email));
        model.addAttribute("upcomingSessions", sessionService.upcoming(email, 5));
        return "dashboard";
    }
}
