package com.gakbisa.backend.controller;

import com.gakbisa.backend.domain.BarterRequest;
import com.gakbisa.backend.domain.BarterStatus;
import com.gakbisa.backend.domain.LearningSession;
import com.gakbisa.backend.domain.Portfolio;
import com.gakbisa.backend.domain.PortfolioVisibility;
import com.gakbisa.backend.domain.SessionStatus;
import com.gakbisa.backend.domain.SkillLevel;
import com.gakbisa.backend.domain.SkillType;
import com.gakbisa.backend.domain.User;
import com.gakbisa.backend.dto.BarterCreateRequest;
import com.gakbisa.backend.dto.BarterRequestDto;
import com.gakbisa.backend.dto.MatchResult;
import com.gakbisa.backend.dto.PortfolioCreateDto;
import com.gakbisa.backend.dto.ProfileUpdateDto;
import com.gakbisa.backend.dto.RegisterRequest;
import com.gakbisa.backend.dto.ReviewCreateRequest;
import com.gakbisa.backend.dto.SessionCreateRequest;
import com.gakbisa.backend.dto.UserSkillDto;
import com.gakbisa.backend.repository.BarterRequestRepository;
import com.gakbisa.backend.repository.LearningSessionRepository;
import com.gakbisa.backend.repository.NotificationRepository;
import com.gakbisa.backend.repository.PortfolioRepository;
import com.gakbisa.backend.repository.SkillCategoryRepository;
import com.gakbisa.backend.repository.SkillRepository;
import com.gakbisa.backend.repository.UserRepository;
import com.gakbisa.backend.service.AuthService;
import com.gakbisa.backend.service.BarterService;
import com.gakbisa.backend.service.MatchingService;
import com.gakbisa.backend.service.NotificationService;
import com.gakbisa.backend.service.PortfolioService;
import com.gakbisa.backend.service.ReviewService;
import com.gakbisa.backend.service.SessionService;
import com.gakbisa.backend.service.UserService;
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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Controller
public class PageController {

    private final AuthService auth;
    private final UserService userService;
    private final UserRepository userRepository;
    private final SkillCategoryRepository categoryRepository;
    private final SkillRepository skillRepository;
    private final MatchingService matchingService;
    private final BarterService barterService;
    private final BarterRequestRepository barterRequestRepository;
    private final PortfolioService portfolioService;
    private final PortfolioRepository portfolioRepository;
    private final SessionService sessionService;
    private final LearningSessionRepository sessionRepository;
    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;
    private final ReviewService reviewService;

    public PageController(AuthService auth, UserService userService, UserRepository userRepository,
                          SkillCategoryRepository categoryRepository, SkillRepository skillRepository,
                          MatchingService matchingService, BarterService barterService,
                          BarterRequestRepository barterRequestRepository, PortfolioService portfolioService,
                          PortfolioRepository portfolioRepository, SessionService sessionService,
                          LearningSessionRepository sessionRepository, NotificationService notificationService,
                          NotificationRepository notificationRepository, ReviewService reviewService) {
        this.auth = auth;
        this.userService = userService;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.skillRepository = skillRepository;
        this.matchingService = matchingService;
        this.barterService = barterService;
        this.barterRequestRepository = barterRequestRepository;
        this.portfolioService = portfolioService;
        this.portfolioRepository = portfolioRepository;
        this.sessionService = sessionService;
        this.sessionRepository = sessionRepository;
        this.notificationService = notificationService;
        this.notificationRepository = notificationRepository;
        this.reviewService = reviewService;
    }

    private UUID currentUserId() {
        try {
            return CurrentUser.id();
        } catch (Exception e) {
            return null;
        }
    }

    // -------------------------------------------------------------
    // Auth & Landing
    // -------------------------------------------------------------

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

    // -------------------------------------------------------------
    // Dashboard & Matching (Algoritma 100 Poin Proposal §4.3)
    // -------------------------------------------------------------

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        UUID uid = currentUserId();
        if (uid != null) {
            var inboxes = barterService.inbox(uid);
            long pending = inboxes.stream().filter(b -> b.getStatus() == BarterStatus.PENDING).count();
            model.addAttribute("pendingBarters", pending);

            var sessions = sessionService.forUser(uid);
            long active = sessions.stream().filter(s -> s.getStatus() == SessionStatus.SCHEDULED || s.getStatus() == SessionStatus.ONGOING).count();
            model.addAttribute("activeSessions", active);

            long unread = notificationRepository.countByUserIdAndReadFalse(uid);
            model.addAttribute("unreadNotifications", unread);

            List<MatchingService.Candidate> candidates = matchingService.recommend(uid, 4);
            List<MatchResult> topMatches = candidates.stream()
                .map(c -> new MatchResult(userRepository.findById(c.userId()).orElse(null), c.score(), c.reasons()))
                .filter(m -> m.getCandidate() != null)
                .toList();
            model.addAttribute("topMatches", topMatches);

            model.addAttribute("recentNotifications", notificationService.inbox(uid).stream().limit(5).toList());
        } else {
            model.addAttribute("pendingBarters", 0);
            model.addAttribute("activeSessions", 0);
            model.addAttribute("unreadNotifications", 0);
            model.addAttribute("topMatches", Collections.emptyList());
            model.addAttribute("recentNotifications", Collections.emptyList());
        }
        return "dashboard";
    }

    @GetMapping("/matching")
    public String matching(@RequestParam(required = false) String keyword, Model model) {
        UUID uid = currentUserId();
        if (uid != null) {
            List<MatchingService.Candidate> candidates = matchingService.recommend(uid, 20);
            String kw = keyword != null ? keyword.trim().toLowerCase() : "";
            List<MatchResult> matches = candidates.stream()
                .filter(c -> kw.isEmpty()
                    || (c.fullName() != null && c.fullName().toLowerCase().contains(kw))
                    || (c.campus() != null && c.campus().toLowerCase().contains(kw))
                    || c.reasons().stream().anyMatch(r -> r.toLowerCase().contains(kw)))
                .map(c -> new MatchResult(userRepository.findById(c.userId()).orElse(null), c.score(), c.reasons()))
                .filter(m -> m.getCandidate() != null)
                .toList();
            model.addAttribute("matches", matches);
            model.addAttribute("keyword", keyword != null ? keyword : "");
        } else {
            model.addAttribute("matches", Collections.emptyList());
            model.addAttribute("keyword", "");
        }
        return "matching/results";
    }

    // -------------------------------------------------------------
    // Barter Workflow (Proposal §4.1 & §5.2 Skenario A)
    // -------------------------------------------------------------

    @GetMapping("/barter")
    public String barter(Model model) {
        UUID uid = currentUserId();
        if (uid != null) {
            model.addAttribute("receivedBarters", barterService.inbox(uid));
            model.addAttribute("sentBarters", barterService.outbox(uid));
        } else {
            model.addAttribute("receivedBarters", Collections.emptyList());
            model.addAttribute("sentBarters", Collections.emptyList());
        }
        return "barter/list";
    }

    @GetMapping("/barter/{id}")
    public String barterDetail(@PathVariable UUID id, Model model) {
        UUID uid = currentUserId();
        BarterRequest b = barterRequestRepository.findById(id).orElse(null);
        model.addAttribute("barter", b);
        var user = uid != null ? userRepository.findById(uid).orElse(null) : null;
        model.addAttribute("user", user);
        return "barter/detail";
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

    @PostMapping("/barter/request/{id}")
    public String sendBarter(@PathVariable UUID id,
                             @ModelAttribute("barterRequestDto") BarterRequestDto dto,
                             RedirectAttributes ra) {
        UUID uid = currentUserId();
        if (uid == null) return "redirect:/login";
        try {
            barterService.send(uid, new BarterCreateRequest(id, dto.getOfferedUserSkillId(), dto.getWantedUserSkillId(), dto.getMessage()));
            ra.addFlashAttribute("success", "Permintaan barter berhasil dikirim!");
            return "redirect:/barter";
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/barter/request/" + id;
        }
    }

    @PostMapping("/barter/{id}/accept")
    public String acceptBarter(@PathVariable UUID id, RedirectAttributes ra) {
        UUID uid = currentUserId();
        if (uid != null) {
            try {
                barterService.respond(uid, id, true);
                ra.addFlashAttribute("success", "Permintaan barter berhasil diterima! Silakan jadwalkan sesi belajar.");
            } catch (Exception e) {
                ra.addFlashAttribute("error", e.getMessage());
            }
        }
        return "redirect:/barter/" + id;
    }

    @PostMapping("/barter/{id}/reject")
    public String rejectBarter(@PathVariable UUID id, RedirectAttributes ra) {
        UUID uid = currentUserId();
        if (uid != null) {
            try {
                barterService.respond(uid, id, false);
                ra.addFlashAttribute("success", "Permintaan barter ditolak.");
            } catch (Exception e) {
                ra.addFlashAttribute("error", e.getMessage());
            }
        }
        return "redirect:/barter";
    }

    @PostMapping("/barter/{id}/cancel")
    public String cancelBarter(@PathVariable UUID id, RedirectAttributes ra) {
        UUID uid = currentUserId();
        if (uid != null) {
            try {
                barterService.cancel(uid, id);
                ra.addFlashAttribute("success", "Permintaan barter dibatalkan.");
            } catch (Exception e) {
                ra.addFlashAttribute("error", e.getMessage());
            }
        }
        return "redirect:/barter";
    }

    // -------------------------------------------------------------
    // Learning Sessions & Video Meeting (Proposal §4.1, §5.2 Skenario B)
    // -------------------------------------------------------------

    @GetMapping("/sessions")
    public String sessions(Model model) {
        UUID uid = currentUserId();
        if (uid != null) {
            model.addAttribute("sessions", sessionService.forUser(uid));
        } else {
            model.addAttribute("sessions", Collections.emptyList());
        }
        return "session/list";
    }

    @GetMapping("/sessions/{id}")
    public String sessionDetail(@PathVariable UUID id, Model model) {
        UUID uid = currentUserId();
        if (uid == null) return "redirect:/login";
        try {
            LearningSession s = sessionService.viewFor(uid, id);
            model.addAttribute("session", s);
            return "session/detail";
        } catch (Exception e) {
            return "redirect:/sessions";
        }
    }

    @PostMapping("/sessions/create/{barterId}")
    public String scheduleSession(@PathVariable UUID barterId,
                                  @RequestParam("scheduledAt") String scheduledAtStr,
                                  @RequestParam(defaultValue = "60") int durationMinutes,
                                  @RequestParam(required = false) String meetingUrl,
                                  RedirectAttributes ra) {
        UUID uid = currentUserId();
        if (uid == null) return "redirect:/login";
        try {
            Instant at = LocalDateTime.parse(scheduledAtStr).atZone(ZoneId.systemDefault()).toInstant();
            sessionService.schedule(uid, new SessionCreateRequest(barterId, at, durationMinutes, meetingUrl));
            ra.addFlashAttribute("success", "Sesi belajar berhasil dijadwalkan!");
            return "redirect:/sessions";
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/barter/" + barterId;
        }
    }

    @PostMapping("/sessions/{id}/complete")
    public String completeSession(@PathVariable UUID id, RedirectAttributes ra) {
        UUID uid = currentUserId();
        if (uid != null) {
            try {
                sessionService.setStatus(uid, id, SessionStatus.COMPLETED);
                ra.addFlashAttribute("success", "Sesi belajar telah selesai! Jangan lupa berikan ulasan.");
            } catch (Exception e) {
                ra.addFlashAttribute("error", e.getMessage());
            }
        }
        return "redirect:/sessions/" + id;
    }

    @PostMapping("/sessions/{id}/cancel")
    public String cancelSession(@PathVariable UUID id, RedirectAttributes ra) {
        UUID uid = currentUserId();
        if (uid != null) {
            try {
                sessionService.setStatus(uid, id, SessionStatus.CANCELLED);
                ra.addFlashAttribute("success", "Sesi belajar dibatalkan.");
            } catch (Exception e) {
                ra.addFlashAttribute("error", e.getMessage());
            }
        }
        return "redirect:/sessions/" + id;
    }

    @PostMapping("/reviews/session/{id}")
    public String submitReview(@PathVariable UUID id,
                               @RequestParam("rating") int rating,
                               @RequestParam(value = "comment", required = false) String comment,
                               RedirectAttributes ra) {
        UUID uid = currentUserId();
        if (uid != null) {
            try {
                reviewService.create(uid, new ReviewCreateRequest(id, (short) rating, comment));
                ra.addFlashAttribute("success", "Terima kasih atas ulasan Anda!");
            } catch (Exception e) {
                ra.addFlashAttribute("error", e.getMessage());
            }
        }
        return "redirect:/sessions/" + id;
    }

    // -------------------------------------------------------------
    // Portofolio (Supabase Storage §6.5 & Skenario C)
    // -------------------------------------------------------------

    @GetMapping("/portfolio")
    public String portfolio(Model model) {
        UUID uid = currentUserId();
        if (uid != null) {
            model.addAttribute("portfolios", portfolioRepository.findByUserId(uid));
            model.addAttribute("portfolioOwner", null);
        } else {
            model.addAttribute("portfolios", Collections.emptyList());
        }
        return "portfolio/gallery";
    }

    @GetMapping("/portfolio/user/{id}")
    public String userPortfolio(@PathVariable UUID id, Model model) {
        var owner = userRepository.findById(id).orElse(null);
        model.addAttribute("portfolioOwner", owner);
        model.addAttribute("portfolios", portfolioRepository.findByUserId(id));
        return "portfolio/gallery";
    }

    @GetMapping("/portfolio/upload")
    public String portfolioUpload(Model model) {
        model.addAttribute("portfolioDto", new PortfolioCreateDto());
        return "portfolio/upload";
    }

    @PostMapping("/portfolio/upload")
    public String portfolioUploadPost(@RequestParam("file") MultipartFile file,
                                      @Valid @ModelAttribute("portfolioDto") PortfolioCreateDto dto,
                                      BindingResult errors,
                                      Model model,
                                      RedirectAttributes ra) {
        UUID uid = currentUserId();
        if (uid == null) return "redirect:/login";
        if (file == null || file.isEmpty()) {
            model.addAttribute("error", "File portofolio wajib dipilih!");
            return "portfolio/upload";
        }
        try {
            PortfolioVisibility vis = "PRIVATE".equalsIgnoreCase(dto.getVisibility())
                ? PortfolioVisibility.PRIVATE
                : PortfolioVisibility.PUBLIC;
            portfolioService.upload(
                uid,
                new PortfolioService.NewPortfolio(dto.getTitle(), dto.getDescription(), vis),
                file.getOriginalFilename(),
                file.getContentType(),
                file.getBytes()
            );
            ra.addFlashAttribute("success", "Portofolio berhasil diunggah ke Supabase Storage!");
            return "redirect:/portfolio";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return "portfolio/upload";
        }
    }

    @PostMapping("/portfolio/{id}/delete")
    public String deletePortfolio(@PathVariable UUID id, RedirectAttributes ra) {
        UUID uid = currentUserId();
        if (uid != null) {
            try {
                portfolioService.delete(uid, id);
                ra.addFlashAttribute("success", "Portofolio berhasil dihapus!");
            } catch (Exception e) {
                ra.addFlashAttribute("error", e.getMessage());
            }
        }
        return "redirect:/portfolio";
    }

    // -------------------------------------------------------------
    // Notifikasi
    // -------------------------------------------------------------

    @GetMapping("/notifications")
    public String notifications(Model model) {
        UUID uid = currentUserId();
        if (uid != null) {
            model.addAttribute("notifications", notificationService.inbox(uid));
        } else {
            model.addAttribute("notifications", Collections.emptyList());
        }
        return "notification/list";
    }

    @PostMapping("/notifications/{id}/read")
    public String markNotificationRead(@PathVariable UUID id) {
        UUID uid = currentUserId();
        if (uid != null) {
            try {
                notificationService.markRead(uid, id);
            } catch (Exception ignored) {}
        }
        return "redirect:/notifications";
    }

    @PostMapping("/notifications/read-all")
    public String markAllNotificationsRead() {
        UUID uid = currentUserId();
        if (uid != null) {
            notificationService.inbox(uid).forEach(n -> {
                if (!n.isRead()) {
                    try { notificationService.markRead(uid, n.getId()); } catch (Exception ignored) {}
                }
            });
        }
        return "redirect:/notifications";
    }

    // -------------------------------------------------------------
    // Profile & Skills Management
    // -------------------------------------------------------------

    @GetMapping("/profile")
    public String profile(Model model) {
        UUID uid = currentUserId();
        var user = uid != null ? userRepository.findById(uid).orElse(null) : null;
        model.addAttribute("user", user);
        if (uid != null) {
            model.addAttribute("offerSkills", userService.listSkills(uid, SkillType.OFFER));
            model.addAttribute("wantSkills", userService.listSkills(uid, SkillType.WANT));
            model.addAttribute("portfolios", portfolioRepository.findByUserId(uid));
        } else {
            model.addAttribute("offerSkills", Collections.emptyList());
            model.addAttribute("wantSkills", Collections.emptyList());
            model.addAttribute("portfolios", Collections.emptyList());
        }
        model.addAttribute("reviews", Collections.emptyList());
        return "profile/view";
    }

    @GetMapping("/profile/{id}")
    public String viewUserProfile(@PathVariable UUID id, Model model) {
        var user = userRepository.findById(id).orElse(null);
        model.addAttribute("user", user);
        model.addAttribute("offerSkills", userService.listSkills(id, SkillType.OFFER));
        model.addAttribute("wantSkills", userService.listSkills(id, SkillType.WANT));
        model.addAttribute("portfolios", portfolioRepository.findByUserId(id));
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

    // -------------------------------------------------------------
    // Admin Views
    // -------------------------------------------------------------

    @GetMapping({"/admin", "/admin/dashboard"})
    public String adminDashboard(Model model) {
        model.addAttribute("totalUsers", userRepository.count());
        model.addAttribute("totalPortfolios", portfolioRepository.count());
        model.addAttribute("totalBarters", barterRequestRepository.count());
        model.addAttribute("totalSessions", sessionRepository.count());
        model.addAttribute("totalReports", 0);
        model.addAttribute("pendingReports", 0);
        model.addAttribute("recentUsers", userRepository.findAll().stream().limit(5).toList());
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
