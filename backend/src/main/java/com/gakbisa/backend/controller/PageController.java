package com.gakbisa.backend.controller;

import com.gakbisa.backend.dto.PortfolioCreateDto;
import com.gakbisa.backend.dto.ProfileUpdateDto;
import com.gakbisa.backend.dto.RegisterRequest;
import com.gakbisa.backend.repository.UserRepository;
import com.gakbisa.backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Collections;

@Controller
public class PageController {

    private final AuthService auth;
    private final UserRepository userRepository;

    public PageController(AuthService auth, UserRepository userRepository) {
        this.auth = auth;
        this.userRepository = userRepository;
    }

    @GetMapping("/")
    public String home() {
        return "home";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("registerForm", new RegisterRequest(null, null, null, null));
        return "register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("registerForm") RegisterRequest req, BindingResult errors, Model model) {
        if (errors.hasErrors()) return "register";
        try {
            auth.register(req);
            return "redirect:/login?registered";
        } catch (com.gakbisa.backend.config.AppExceptions.ValidationException e) {
            model.addAttribute("error", e.getMessage());
            return "register";
        }
    }

    @GetMapping("/catalog")
    public String catalog() {
        return "catalog";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("pendingBarters", 0);
        model.addAttribute("activeSessions", 0);
        model.addAttribute("unreadNotifications", 0);
        model.addAttribute("topMatches", Collections.emptyList());
        model.addAttribute("recentNotifications", Collections.emptyList());
        return "dashboard";
    }

    @GetMapping("/matching")
    public String matching(Model model) {
        model.addAttribute("matches", Collections.emptyList());
        model.addAttribute("keyword", "");
        return "matching/results";
    }

    @GetMapping("/barter")
    public String barter(Model model) {
        model.addAttribute("receivedBarters", Collections.emptyList());
        model.addAttribute("sentBarters", Collections.emptyList());
        return "barter/list";
    }

    @GetMapping("/sessions")
    public String sessions(Model model) {
        model.addAttribute("sessions", Collections.emptyList());
        return "session/list";
    }

    @GetMapping("/portfolio")
    public String portfolio(Model model) {
        model.addAttribute("portfolios", Collections.emptyList());
        return "portfolio/gallery";
    }

    @GetMapping("/portfolio/upload")
    public String portfolioUpload(Model model) {
        model.addAttribute("portfolioDto", new PortfolioCreateDto());
        return "portfolio/upload";
    }

    @GetMapping("/notifications")
    public String notifications(Model model) {
        model.addAttribute("notifications", Collections.emptyList());
        return "notification/list";
    }

    @GetMapping("/profile")
    public String profile(Model model) {
        var user = userRepository.findById(CurrentUser.id()).orElse(null);
        model.addAttribute("user", user);
        model.addAttribute("offerSkills", Collections.emptyList());
        model.addAttribute("wantSkills", Collections.emptyList());
        model.addAttribute("portfolios", Collections.emptyList());
        model.addAttribute("reviews", Collections.emptyList());
        return "profile/view";
    }

    @GetMapping("/profile/skills")
    public String profileSkills(Model model) {
        model.addAttribute("offerSkills", Collections.emptyList());
        model.addAttribute("wantSkills", Collections.emptyList());
        model.addAttribute("categories", Collections.emptyList());
        return "profile/skills";
    }

    @GetMapping("/profile/edit")
    public String profileEdit(Model model) {
        var user = userRepository.findById(CurrentUser.id()).orElse(null);
        ProfileUpdateDto dto = new ProfileUpdateDto(
            user != null ? user.getFullName() : "",
            user != null ? user.getBio() : "",
            user != null ? user.getCampus() : ""
        );
        model.addAttribute("profileUpdateDto", dto);
        return "profile/edit";
    }

    @GetMapping("/access-denied")
    public String denied() {
        return "denied";
    }
}
