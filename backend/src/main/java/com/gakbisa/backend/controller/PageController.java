package com.gakbisa.backend.controller;

import com.gakbisa.backend.dto.RegisterRequest;
import com.gakbisa.backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class PageController {

    private final AuthService auth;

    public PageController(AuthService auth) {
        this.auth = auth;
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
        model.addAttribute("user", CurrentUser.id());
        return "dashboard";
    }

    @GetMapping("/access-denied")
    public String denied() {
        return "denied";
    }
}
