package com.gakbisa.controller;

import com.gakbisa.dto.Forms.RegisterForm;
import com.gakbisa.exception.BusinessException;
import com.gakbisa.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

/**
 * Registrasi & halaman login.
 * POST /login dan /logout ditangani Spring Security (formLogin/logout di SecurityConfig).
 */
@Controller
public class AuthController extends BaseController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/login")
    public String loginPage(Principal principal) {
        return principal != null ? "redirect:/dashboard" : "auth/login";
    }

    @GetMapping("/register")
    public String registerPage(Principal principal, Model model) {
        if (principal != null) {
            return "redirect:/dashboard";
        }
        model.addAttribute("form", new RegisterForm());
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegisterForm form,
                           BindingResult result,
                           RedirectAttributes ra) {
        if (form.getPassword() != null && !form.getPassword().equals(form.getConfirmPassword())) {
            result.rejectValue("confirmPassword", "mismatch", "Konfirmasi password tidak sama");
        }
        if (result.hasErrors()) {
            return "auth/register";
        }
        try {
            authService.register(form);
        } catch (BusinessException e) {
            result.rejectValue("email", "duplicate", e.getMessage());
            return "auth/register";
        }
        ra.addFlashAttribute("success", "Registrasi berhasil. Silakan login.");
        return "redirect:/login";
    }
}
