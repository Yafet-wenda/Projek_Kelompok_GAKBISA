package com.gakbisa.backend.controller;

import com.gakbisa.backend.domain.AccountStatus;
import com.gakbisa.backend.domain.BarterRequest;
import com.gakbisa.backend.domain.BarterStatus;
import com.gakbisa.backend.domain.LearningSession;
import com.gakbisa.backend.domain.Portfolio;
import com.gakbisa.backend.domain.PortfolioVisibility;
import com.gakbisa.backend.domain.Report;
import com.gakbisa.backend.domain.ReportStatus;
import com.gakbisa.backend.domain.ReportTargetType;
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
import com.gakbisa.backend.dto.ReportRow;
import com.gakbisa.backend.dto.ReviewCreateRequest;
import com.gakbisa.backend.dto.ReviewRow;
import com.gakbisa.backend.dto.SessionCreateRequest;
import com.gakbisa.backend.dto.UserSkillDto;
import com.gakbisa.backend.repository.BarterRequestRepository;
import com.gakbisa.backend.repository.LearningSessionRepository;
import com.gakbisa.backend.repository.NotificationRepository;
import com.gakbisa.backend.repository.PortfolioRepository;
import com.gakbisa.backend.repository.SkillCategoryRepository;
import com.gakbisa.backend.repository.SkillRepository;
import com.gakbisa.backend.repository.UserRepository;
import com.gakbisa.backend.service.AdminService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
    private final AdminService adminService;

    public PageController(AuthService auth, UserService userService, UserRepository userRepository,
                          SkillCategoryRepository categoryRepository, SkillRepository skillRepository,
                          MatchingService matchingService, BarterService barterService,
                          BarterRequestRepository barterRequestRepository, PortfolioService portfolioService,
                          PortfolioRepository portfolioRepository, SessionService sessionService,
                          LearningSessionRepository sessionRepository, NotificationService notificationService,
                          NotificationRepository notificationRepository, ReviewService reviewService,
                          AdminService adminService) {
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
        this.adminService = adminService;
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

    /** Halaman referensi komponen — hanya untuk developer, login required. */
    @GetMapping("/design-system")
    public String designSystem() {
        return "design-system";
    }

    @GetMapping("/login")
    public String login() {
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("registerForm", new RegisterRequest(null, null, null, null));
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("registerForm") RegisterRequest req, BindingResult errors, Model model) {
        if (errors.hasErrors()) return "auth/register";
        try {
            auth.register(req);
            return "redirect:/login?registered";
        } catch (com.gakbisa.backend.config.AppExceptions.ValidationException e) {
            model.addAttribute("error", e.getMessage());
            return "auth/register";
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
            // Nama variabel "session" Reserved oleh Thymeleaf (sesi HTTP), jadi halaman
            // ini pakai "sesi"; kalau tidak, halaman melempar 500 saat dirender.
            model.addAttribute("sesi", s);
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

    @GetMapping("/portfolio/{id}/edit")
    public String portfolioEdit(@PathVariable UUID id, Model model, RedirectAttributes ra) {
        UUID uid = currentUserId();
        if (uid == null) return "redirect:/login";
        try {
            model.addAttribute("portfolio", portfolioService.requireOwned(uid, id));
            return "portfolio/edit";
        } catch (Exception e) {
            ra.addFlashAttribute("error", messageOf(e, "Portofolio tidak ditemukan."));
            return "redirect:/portfolio";
        }
    }

    @PostMapping("/portfolio/{id}/edit")
    public String portfolioEditPost(@PathVariable UUID id,
                                    @RequestParam String title,
                                    @RequestParam(required = false) String description,
                                    @RequestParam PortfolioVisibility visibility,
                                    RedirectAttributes ra) {
        UUID uid = currentUserId();
        if (uid == null) return "redirect:/login";
        try {
            portfolioService.update(uid, id, title, description, visibility);
            ra.addFlashAttribute("success", "Perubahan portofolio tersimpan.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", messageOf(e, "Gagal menyimpan perubahan."));
        }
        return "redirect:/portfolio";
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
        addReviews(model, uid);
        return "profile/view";
    }

    @GetMapping("/profile/{id}")
    public String viewUserProfile(@PathVariable UUID id, Model model) {
        var user = userRepository.findById(id).orElse(null);
        model.addAttribute("user", user);
        model.addAttribute("offerSkills", userService.listSkills(id, SkillType.OFFER));
        model.addAttribute("wantSkills", userService.listSkills(id, SkillType.WANT));
        model.addAttribute("portfolios", portfolioRepository.findByUserId(id));
        addReviews(model, id);
        return "profile/view";
    }

    /** Ulasan yang diterima plus rata-rata ratingnya; dipakai kartu "Ulasan" di profil. */
    private void addReviews(Model model, UUID userId) {
        if (userId == null) {
            model.addAttribute("reviews", Collections.emptyList());
            return;
        }
        model.addAttribute("reviews", reviewService.forUser(userId).stream()
            .map(rv -> new ReviewRow(rv.getReviewer().getFullName(), rv.getRating(),
                rv.getComment(), rv.getCreatedAt()))
            .toList());
        var reputasi = userService.reputation(userId);
        model.addAttribute("ratingAvg", reputasi.averageRating());
        model.addAttribute("ratingCount", reputasi.reviewCount());
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
        model.addAttribute("skills", skillRepository.findAllWithCategory());
        model.addAttribute("userSkillDto", new UserSkillDto());
        return "profile/skills";
    }

    @PostMapping("/profile/skills")
    public String addSkill(@Valid @ModelAttribute("userSkillDto") UserSkillDto dto, BindingResult errors,
                           RedirectAttributes ra) {
        UUID uid = currentUserId();
        boolean adaPilihan = dto.getSkillId() != null;
        boolean adaNama = dto.getSkillName() != null && !dto.getSkillName().isBlank();
        if (!adaPilihan && !adaNama) {
            // Id dari form bisa gagal terikat (bukan UUID), jadi dua-duanya kosong di sini.
            ra.addFlashAttribute("error", "Pilih keahlian dari katalog atau tulis namanya");
            return "redirect:/profile/skills";
        }
        if (uid != null) {
            try {
                SkillType st;
                try {
                    st = SkillType.valueOf(dto.getType());
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Tipe keahlian tidak dikenali");
                }
                SkillLevel sl = dto.getLevel() != null && !dto.getLevel().isBlank()
                    ? SkillLevel.valueOf(dto.getLevel())
                    : SkillLevel.BEGINNER;
                userService.addSkill(uid, dto.getSkillId(), dto.getSkillName(), st, sl, dto.getNotes());
                ra.addFlashAttribute("success", "Keahlian ditambahkan ke daftar kamu.");
                return "redirect:/profile/skills";
            } catch (Exception e) {
                ra.addFlashAttribute("error", e.getMessage());
                return "redirect:/profile/skills";
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
        Map<String, Long> ringkasan = adminService.dashboardSummary();
        model.addAttribute("totalUsers", ringkasan.get("users"));
        model.addAttribute("totalPortfolios", ringkasan.get("portfolios"));
        model.addAttribute("totalBarters", ringkasan.get("barterRequests"));
        model.addAttribute("totalSessions", sessionRepository.count());
        model.addAttribute("openReports", ringkasan.get("openReports"));
        model.addAttribute("recentReports", reportRows(adminService.pendingReports(5)));
        return "admin/dashboard";
    }

    @GetMapping("/admin/users")
    public String adminUsers(Model model) {
        model.addAttribute("users", userRepository.findAll());
        return "admin/users";
    }

    @PostMapping("/admin/users/{id}/suspend")
    public String suspendUser(@PathVariable UUID id, RedirectAttributes ra) {
        return changeAccountStatus(id, AccountStatus.SUSPENDED, ra, "Akun dibatasi.");
    }

    @PostMapping("/admin/users/{id}/activate")
    public String activateUser(@PathVariable UUID id, RedirectAttributes ra) {
        return changeAccountStatus(id, AccountStatus.ACTIVE, ra, "Akun diaktifkan kembali.");
    }

    private String changeAccountStatus(UUID targetId, AccountStatus status, RedirectAttributes ra, String ok) {
        UUID uid = currentUserId();
        if (uid == null) return "redirect:/login";
        try {
            User target = adminService.setAccountStatus(uid, targetId, status);
            ra.addFlashAttribute("success", target.getFullName() + ": " + ok.toLowerCase());
        } catch (Exception e) {
            ra.addFlashAttribute("error", messageOf(e, "Gagal mengubah status akun."));
        }
        return "redirect:/admin/users";
    }

    /** Antrean moderasi. Tanpa status, yang ditampilkan yang masih menunggu keputusan. */
    @GetMapping("/admin/reports")
    public String adminReports(@RequestParam(required = false) ReportStatus status, Model model) {
        List<Report> laporan = status != null
            ? adminService.reportsByStatus(status)
            : adminService.pendingReports(50);
        model.addAttribute("reports", reportRows(laporan));
        model.addAttribute("activeStatus", status);
        return "admin/reports";
    }

    @PostMapping("/admin/reports/{id}/review")
    public String reviewReport(@PathVariable UUID id,
                               @RequestParam ReportStatus decision,
                               @RequestParam(defaultValue = "false") boolean hidePortfolio,
                               @RequestParam(defaultValue = "false") boolean suspendUser,
                               RedirectAttributes ra) {
        UUID uid = currentUserId();
        if (uid == null) return "redirect:/login";
        try {
            adminService.reviewReport(uid, id, decision, hidePortfolio, suspendUser);
            ra.addFlashAttribute("success", decision == ReportStatus.DISMISSED
                ? "Laporan diabaikan."
                : "Laporan selesai ditindak.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", messageOf(e, "Gagal memproses laporan."));
        }
        return "redirect:/admin/reports";
    }

    @GetMapping("/admin/categories")
    public String adminCategories(Model model) {
        model.addAttribute("categories", adminService.categoriesWithSkills());
        return "admin/categories";
    }

    @PostMapping("/admin/categories")
    public String addCategory(@RequestParam String name,
                              @RequestParam(required = false) String description,
                              RedirectAttributes ra) {
        try {
            var saved = adminService.upsertCategory(name, description);
            ra.addFlashAttribute("success", "Kategori " + saved.getName() + " tersimpan.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", messageOf(e, "Gagal menyimpan kategori."));
        }
        return "redirect:/admin/categories";
    }

    @PostMapping("/admin/categories/{id}/skills")
    public String addCategorySkill(@PathVariable UUID id,
                                   @RequestParam String name,
                                   @RequestParam(required = false) String description,
                                   RedirectAttributes ra) {
        try {
            var saved = adminService.upsertSkill(name, id, description);
            ra.addFlashAttribute("success", "Keahlian " + saved.getName() + " tersimpan.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", messageOf(e, "Gagal menyimpan keahlian."));
        }
        return "redirect:/admin/categories";
    }

    /** Nama pelapor dan sasaran diselesaikan di sini: open-in-view dimatikan, jadi
     * template tidak bisa menarik relasi LAZY-nya sendiri. */
    private List<ReportRow> reportRows(List<Report> laporan) {
        return laporan.stream().map(r -> {
            if (r.getTargetType() == ReportTargetType.USER) {
                User target = userRepository.findById(r.getTargetId()).orElse(null);
                return new ReportRow(r.getId(), r.getReason(), r.getReporter().getFullName(), r.getStatus().name(),
                    "Pengguna", target == null ? "Sudah terhapus" : target.getFullName(),
                    target == null ? null : "/profile/" + target.getId(), r.getCreatedAt());
            }
            String judul = portfolioRepository.findById(r.getTargetId())
                .map(Portfolio::getTitle).orElse("Sudah terhapus");
            return new ReportRow(r.getId(), r.getReason(), r.getReporter().getFullName(), r.getStatus().name(),
                "Portofolio", judul, null, r.getCreatedAt());
        }).toList();
    }

    private String messageOf(Exception e, String fallback) {
        return e.getMessage() != null && !e.getMessage().isBlank() ? e.getMessage() : fallback;
    }

    /**
     * Ditampilkan accessDeniedHandler lewat request dispatcher include, jadi harus menerima
     * metode asal: 403 dari form POST kalau hanya dipetakan ke GET akan berakhir halaman kosong.
     */
    @RequestMapping("/access-denied")
    public String denied() {
        return "denied";
    }
}
