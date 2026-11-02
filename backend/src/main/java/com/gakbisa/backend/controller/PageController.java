package com.gakbisa.backend.controller;

import com.gakbisa.backend.domain.SkillLevel;
import com.gakbisa.backend.domain.SkillType;
import com.gakbisa.backend.dto.BarterRequestDto;
import com.gakbisa.backend.dto.PortfolioCreateDto;
import com.gakbisa.backend.dto.ProfileUpdateDto;
import com.gakbisa.backend.dto.RegisterRequest;
import com.gakbisa.backend.dto.UserSkillDto;
import com.gakbisa.backend.repository.SkillCategoryRepository;
import com.gakbisa.backend.repository.SkillRepository;
import com.gakbisa.backend.repository.UserRepository;
import com.gakbisa.backend.service.AuthService;
import com.gakbisa.backend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Collections;
import java.util.UUID;

@Controller
public class PageController {

    private final AuthService auth;
    private final UserService userService;
    private final UserRepository userRepository;
    private final SkillCategoryRepository categoryRepository;
    private final SkillRepository skillRepository;

    public PageController(AuthService auth, UserService userService, UserRepository userRepository,
                          SkillCategoryRepository categoryRepository, SkillRepository skillRepository) {
        this.auth = auth;
        this.userService = userService;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.skillRepository = skillRepository;
    }

    private UUID currentUserId() {
        try {
            return CurrentUser.id();
        } catch (Exception e) {
            return null;
        }
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

    @GetMapping("/barter/request/{id}")
    public String barterRequest(@PathVariable UUID id, Model model) {
        UUID uid = currentUserId();
        var targetUser = userRepository.findById(id).orElse(null);
        model.addAttribute("targetUser", targetUser);
        if (uid != null) {
            model.addAttribute("myOfferSkills", userService.listSkills(uid, SkillType.OFFER));
            model.addAttribute("myWantSkills", userService.listSkills(uid, SkillType.WANT));
        } else {
            model.addAttribute("myOfferSkills", Collections.emptyList());
            model.addAttribute("myWantSkills", Collections.emptyList());
        }
        model.addAttribute("barterRequestDto", new BarterRequestDto());
        return "barter/request";
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

    @GetMapping("/portfolio/user/{id}")
    public String userPortfolio(@PathVariable UUID id, Model model) {
        var owner = userRepository.findById(id).orElse(null);
        model.addAttribute("portfolioOwner", owner);
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
        UUID uid = currentUserId();
        var user = uid != null ? userRepository.findById(uid).orElse(null) : null;
        model.addAttribute("user", user);
        if (uid != null) {
            model.addAttribute("offerSkills", userService.listSkills(uid, SkillType.OFFER));
            model.addAttribute("wantSkills", userService.listSkills(uid, SkillType.WANT));
        } else {
            model.addAttribute("offerSkills", Collections.emptyList());
            model.addAttribute("wantSkills", Collections.emptyList());
        }
        model.addAttribute("portfolios", Collections.emptyList());
        model.addAttribute("reviews", Collections.emptyList());
        return "profile/view";
    }

    @GetMapping("/profile/{id}")
    public String viewUserProfile(@PathVariable UUID id, Model model) {
        var user = userRepository.findById(id).orElse(null);
        model.addAttribute("user", user);
        model.addAttribute("offerSkills", userService.listSkills(id, SkillType.OFFER));
        model.addAttribute("wantSkills", userService.listSkills(id, SkillType.WANT));
        model.addAttribute("portfolios", Collections.emptyList());
        model.addAttribute("reviews", Collections.emptyList());
        return "profile/view";
    }

    @GetMapping("/profile/skills")
    public String profileSkills(Model model) {
        UUID uid = currentUserId();
        if (uid != null) {
            model.addAttribute("offerSkills", userService.listSkills(uid, SkillType.OFFER));
            model.addAttribute("wantSkills", userService.listSkills(uid, SkillType.WANT));
        } else {
            model.addAttribute("offerSkills", Collections.emptyList());
            model.addAttribute("wantSkills", Collections.emptyList());
        }
        model.addAttribute("categories", categoryRepository.findAll());
        model.addAttribute("skills", skillRepository.findAll());
        model.addAttribute("userSkillDto", new UserSkillDto());
        return "profile/skills";
    }

    @PostMapping("/profile/skills")
    public String addSkill(@Valid @ModelAttribute("userSkillDto") UserSkillDto dto, BindingResult errors) {
        UUID uid = currentUserId();
        if (uid != null && dto.getSkillId() != null) {
            try {
                SkillType st = SkillType.valueOf(dto.getType());
                SkillLevel sl = dto.getLevel() != null && !dto.getLevel().isBlank()
                    ? SkillLevel.valueOf(dto.getLevel())
                    : SkillLevel.BEGINNER;
                userService.addSkill(uid, dto.getSkillId(), st, sl, dto.getNotes());
                return "redirect:/profile/skills?success";
            } catch (Exception e) {
                return "redirect:/profile/skills?error=" + java.net.URLEncoder.encode(e.getMessage(), java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        return "redirect:/profile/skills";
    }

    @PostMapping("/profile/skills/{id}/delete")
    public String deleteSkill(@PathVariable UUID id) {
        UUID uid = currentUserId();
        if (uid != null) {
            try {
                userService.removeSkill(uid, id);
            } catch (Exception ignored) {}
        }
        return "redirect:/profile/skills?deleted";
    }

    @GetMapping("/profile/edit")
    public String profileEdit(Model model) {
        UUID uid = currentUserId();
        var user = uid != null ? userRepository.findById(uid).orElse(null) : null;
        ProfileUpdateDto dto = new ProfileUpdateDto(
            user != null ? user.getFullName() : "",
            user != null ? user.getBio() : "",
            user != null ? user.getCampus() : ""
        );
        model.addAttribute("profileUpdateDto", dto);
        return "profile/edit";
    }

    @PostMapping("/profile/edit")
    public String updateProfile(@Valid @ModelAttribute("profileUpdateDto") ProfileUpdateDto dto, BindingResult errors) {
        UUID uid = currentUserId();
        if (uid != null) {
            try {
                userService.updateProfile(uid, uid, dto.getFullName(), dto.getBio(), dto.getCampus());
                return "redirect:/profile?updated";
            } catch (Exception ignored) {}
        }
        return "redirect:/profile";
    }

    @GetMapping({"/admin", "/admin/dashboard"})
    public String adminDashboard(Model model) {
        model.addAttribute("totalUsers", userRepository.count());
        model.addAttribute("totalPortfolios", 0);
        model.addAttribute("totalBarters", 0);
        model.addAttribute("totalSessions", 0);
        model.addAttribute("totalReports", 0);
        model.addAttribute("pendingReports", 0);
        model.addAttribute("recentUsers", Collections.emptyList());
        model.addAttribute("recentReports", Collections.emptyList());
        return "admin/dashboard";
    }

    @GetMapping("/admin/users")
    public String adminUsers(Model model) {
        model.addAttribute("users", userRepository.findAll());
        return "admin/users";
    }

    @GetMapping("/access-denied")
    public String denied() {
        return "denied";
    }
}
