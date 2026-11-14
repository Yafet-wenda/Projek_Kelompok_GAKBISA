package com.gakbisa.controller;

import com.gakbisa.dto.Forms.ProfileForm;
import com.gakbisa.service.UserService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.UUID;

@Controller
public class ProfileController extends BaseController {

    private final UserService userService;

    public ProfileController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/profile")
    public String myProfile(Principal principal, Model model) {
        model.addAttribute("profile", userService.getOwnProfile(principal.getName()));
        model.addAttribute("own", true);
        return "profile/view";
    }

    @GetMapping("/users/{id}")
    public String publicProfile(@PathVariable UUID id, Principal principal, Model model) {
        model.addAttribute("profile",
                userService.getPublicProfile(id, principal != null ? principal.getName() : null));
        model.addAttribute("own", false);
        return "profile/view";
    }

    @GetMapping("/profile/edit")
    public String editForm(Principal principal, Model model) {
        model.addAttribute("form", userService.getProfileForm(principal.getName()));
        return "profile/edit";
    }

    @PostMapping("/profile/edit")
    public String update(@Valid @ModelAttribute("form") ProfileForm form,
                         BindingResult result,
                         @RequestParam(value = "avatar", required = false) MultipartFile avatar,
                         Principal principal,
                         RedirectAttributes ra) {
        if (result.hasErrors()) {
            return "profile/edit";
        }
        return perform(ra, "Profil berhasil diperbarui", "/profile",
                () -> userService.updateProfile(principal.getName(), form, avatar));
    }
}
