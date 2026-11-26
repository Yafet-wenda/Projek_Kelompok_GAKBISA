package com.gakbisa.backend.controller;

import com.gakbisa.backend.config.AppExceptions.ForbiddenException;
import com.gakbisa.backend.config.AppExceptions.NotFoundException;
import com.gakbisa.backend.config.AppExceptions.UnauthorizedException;
import com.gakbisa.backend.config.AppExceptions.ValidationException;
import com.gakbisa.backend.controller.ReportAdminController.CreateReportRequest;
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
import com.gakbisa.backend.security.GakbisaUserDetails;
import com.gakbisa.backend.service.AdminService;
import com.gakbisa.backend.service.AuthService;
import com.gakbisa.backend.service.BarterService;
import com.gakbisa.backend.service.MatchingService;
import com.gakbisa.backend.service.NotificationService;
import com.gakbisa.backend.service.PortfolioService;
import com.gakbisa.backend.service.ReviewService;
import com.gakbisa.backend.service.SessionService;
import com.gakbisa.backend.service.UserService;
import com.gakbisa.backend.service.ProfileMediaService;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import org.springframework.security.core.context.SecurityContextHolder;
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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Session-authenticated HTML pages; JSON endpoints remain in the /api controllers. */
@Controller
public class WebController {

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
    private final Validator validator;
    private final ProfileMediaService profileMedia;

    public WebController(AuthService auth, UserService userService, UserRepository userRepository,
                         SkillCategoryRepository categoryRepository, SkillRepository skillRepository,
                         MatchingService matchingService, BarterService barterService,
                         BarterRequestRepository barterRequestRepository, PortfolioService portfolioService,
                         PortfolioRepository portfolioRepository, SessionService sessionService,
                         LearningSessionRepository sessionRepository, NotificationService notificationService,
                         NotificationRepository notificationRepository, ReviewService reviewService,
                         AdminService adminService, Validator validator, ProfileMediaService profileMedia) {
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
        this.validator = validator;
        this.profileMedia = profileMedia;
    }

    private UUID currentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
            && authentication.getPrincipal() instanceof GakbisaUserDetails details
            ? details.getUser().getId() : null;
    }

    @ModelAttribute("viewer")
    public User viewer() {
        UUID id = currentUserId();
        return id == null ? null : requireUser(id);
    }

    private User requireUser(UUID id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Pengguna tidak ditemukan"));
    }

    // Auth & landing

    @GetMapping("/")
    public String home() {
        return currentUserId() == null ? "redirect:/login" : "redirect:/dashboard";
    }

    @GetMapping("/index.html")
    public String index() {
        return "redirect:/";
    }

    @GetMapping("/login")
    public String login() {
        return currentUserId() == null ? "auth/login" : "redirect:/dashboard";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("registerForm", new RegisterRequest(null, null, null, null));
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("registerForm") RegisterRequest req,
                           BindingResult errors, Model model) {
        if (errors.hasErrors()) return "auth/register";
        try {
            auth.register(req);
            return "redirect:/login?registered";
        } catch (ValidationException | ForbiddenException | NotFoundException | UnauthorizedException
                 | jakarta.validation.ValidationException e) {
            model.addAttribute("error", "Pendaftaran belum berhasil. Periksa data atau masuk jika sudah memiliki akun.");
            return "auth/register";
        }
    }

    @GetMapping("/catalog")
    public String catalog() {
        return "catalog";
    }

    // Dashboard & matching

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        UUID uid = CurrentUser.id();
        if (CurrentUser.isAdmin()) return "redirect:/admin/dashboard";
        model.addAttribute("pendingBarters", barterService.inbox(uid).stream()
            .filter(b -> b.getStatus() == BarterStatus.PENDING).count());
        model.addAttribute("activeSessions", sessionService.forUser(uid).stream()
            .filter(s -> s.getStatus() == SessionStatus.SCHEDULED || s.getStatus() == SessionStatus.ONGOING).count());
        model.addAttribute("unreadNotifications", notificationRepository.countByUserIdAndReadFalse(uid));
        model.addAttribute("topMatches", matchingService.recommend(uid, 4).stream()
            .map(c -> new MatchResult(requireUser(c.userId()), c.score(), c.reasons())).toList());
        model.addAttribute("recentNotifications", notificationService.inbox(uid).stream().limit(5).toList());
        return "dashboard";
    }

    @GetMapping("/matching")
    public String matching(@RequestParam(required = false) String keyword, Model model) {
        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        model.addAttribute("matches", matchingService.recommend(CurrentUser.id(), 20).stream()
            .filter(c -> kw.isEmpty()
                || (c.fullName() != null && c.fullName().toLowerCase(Locale.ROOT).contains(kw))
                || (c.campus() != null && c.campus().toLowerCase(Locale.ROOT).contains(kw))
                || c.reasons().stream().anyMatch(r -> r.toLowerCase(Locale.ROOT).contains(kw)))
            .map(c -> new MatchResult(requireUser(c.userId()), c.score(), c.reasons())).toList());
        model.addAttribute("keyword", keyword == null ? "" : keyword);
        return "matching/results";
    }

    // Barter workflow

    @GetMapping("/barter")
    public String barter(Model model) {
        UUID uid = CurrentUser.id();
        model.addAttribute("receivedBarters", barterService.inbox(uid));
        model.addAttribute("sentBarters", barterService.outbox(uid));
        return "barter/list";
    }

    @GetMapping("/barter/{id}")
    public String barterDetail(@PathVariable UUID id, Model model) {
        UUID uid = CurrentUser.id();
        // findById retains the archived fetch graph; never render an outsider's barter.
        BarterRequest b = barterRequestRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Permintaan barter tidak ditemukan"));
        boolean requester = uid.equals(b.getRequester().getId());
        boolean receiver = uid.equals(b.getTargetUser().getId());
        if (!requester && !receiver) throw new ForbiddenException("Anda bukan peserta barter ini");
        model.addAttribute("barter", b);
        model.addAttribute("user", requireUser(uid));
        model.addAttribute("canAccept", receiver && b.getStatus() == BarterStatus.PENDING);
        model.addAttribute("canCancel", requester
            && (b.getStatus() == BarterStatus.PENDING || b.getStatus() == BarterStatus.ACCEPTED));
        return "barter/detail";
    }

    @GetMapping("/barter/request/{id}")
    public String barterRequest(@PathVariable UUID id, Model model) {
        populateBarterForm(id, model);
        model.addAttribute("barterRequestDto", new BarterRequestDto());
        return "barter/request";
    }

    private void populateBarterForm(UUID targetId, Model model) {
        UUID uid = CurrentUser.id();
        model.addAttribute("targetUser", requireUser(targetId));
        model.addAttribute("myOfferSkills", userService.listSkills(uid, SkillType.OFFER));
        model.addAttribute("myWantSkills", userService.listSkills(uid, SkillType.WANT));
    }

    @PostMapping("/barter/request/{id}")
    public String sendBarter(@PathVariable UUID id,
                            @Valid @ModelAttribute("barterRequestDto") BarterRequestDto dto,
                            BindingResult errors, Model model, RedirectAttributes ra) {
        if (dto.getOfferedUserSkillId() == null) errors.rejectValue("offeredUserSkillId", "required", "Pilih keahlian yang ditawarkan.");
        if (dto.getWantedUserSkillId() == null) errors.rejectValue("wantedUserSkillId", "required", "Pilih keahlian yang ingin dipelajari.");
        if (errors.hasErrors()) {
            populateBarterForm(id, model);
            return "barter/request";
        }
        UUID uid = CurrentUser.id();
        try {
            BarterCreateRequest req = new BarterCreateRequest(id, dto.getOfferedUserSkillId(), dto.getWantedUserSkillId(), dto.getMessage());
            validate(req);
            barterService.send(uid, req);
            ra.addFlashAttribute("success", "Permintaan barter berhasil dikirim!");
            return "redirect:/barter";
        } catch (ValidationException | ForbiddenException | NotFoundException | UnauthorizedException
                 | jakarta.validation.ValidationException e) {
            model.addAttribute("error", "Permintaan tidak dapat dikirim. Periksa pasangan keahlian dan permintaan yang masih aktif.");
            populateBarterForm(id, model);
            return "barter/request";
        }
    }

    @PostMapping("/barter/{id}/accept")
    public String acceptBarter(@PathVariable UUID id, RedirectAttributes ra) {
        UUID uid = CurrentUser.id();
        formAction(() -> barterService.respond(uid, id, true), ra,
            "Permintaan barter diterima! Silakan jadwalkan sesi belajar.", "Permintaan tidak dapat diterima. Periksa status dan penerimanya.");
        return "redirect:/barter/" + id;
    }

    @PostMapping("/barter/{id}/reject")
    public String rejectBarter(@PathVariable UUID id, RedirectAttributes ra) {
        UUID uid = CurrentUser.id();
        formAction(() -> barterService.respond(uid, id, false), ra,
            "Permintaan barter ditolak.", "Permintaan tidak dapat ditolak. Periksa status dan penerimanya.");
        return "redirect:/barter";
    }

    @PostMapping("/barter/{id}/cancel")
    public String cancelBarter(@PathVariable UUID id, RedirectAttributes ra) {
        UUID uid = CurrentUser.id();
        formAction(() -> barterService.cancel(uid, id), ra,
            "Permintaan barter dibatalkan.", "Permintaan tidak dapat dibatalkan. Periksa status dan pengirimnya.");
        return "redirect:/barter";
    }

    // Learning sessions

    @GetMapping("/sessions")
    public String sessions(Model model) {
        model.addAttribute("sessions", sessionService.forUser(CurrentUser.id()));
        return "session/list";
    }

    @GetMapping("/sessions/{id}")
    public String sessionDetail(@PathVariable UUID id, Model model) {
        UUID uid = CurrentUser.id();
        LearningSession s = sessionService.viewFor(uid, id);
        BarterRequest b = s.getBarterRequest();
        boolean accepted = b.getStatus() == BarterStatus.ACCEPTED;
        boolean scheduled = s.getStatus() == SessionStatus.SCHEDULED;
        boolean active = scheduled || s.getStatus() == SessionStatus.ONGOING;
        // "session" is reserved by Thymeleaf for the HTTP session.
        model.addAttribute("sesi", s);
        model.addAttribute("partnerName", uid.equals(b.getRequester().getId())
            ? b.getTargetUser().getFullName() : b.getRequester().getFullName());
        model.addAttribute("canStart", accepted && scheduled
            && Duration.between(Instant.now(), s.getScheduledAt()).abs().toMinutes() <= 60);
        model.addAttribute("canComplete", accepted && active);
        model.addAttribute("canCancel", accepted && active);
        model.addAttribute("canReschedule", accepted && scheduled);
        model.addAttribute("hasReviewed", reviewService.writtenBy(uid).stream()
            .anyMatch(r -> id.equals(r.getSession().getId())));
        return "session/detail";
    }

    @PostMapping("/sessions/create/{barterId}")
    public String scheduleSession(@PathVariable UUID barterId,
                                  @RequestParam("scheduledAt") String scheduledAtStr,
                                  @RequestParam(defaultValue = "60") int durationMinutes,
                                  @RequestParam int timezoneOffset,
                                  @RequestParam(defaultValue = "JITSI") String meetingProvider,
                                  @RequestParam(required = false) String meetingUrl,
                                  RedirectAttributes ra) {
        UUID uid = CurrentUser.id();
        boolean saved = formAction(() -> sessionService.schedule(uid,
            sessionRequest(barterId, scheduledAtStr, durationMinutes, timezoneOffset,
                externalMeetingUrl(meetingProvider, meetingUrl))), ra,
            "Sesi belajar berhasil dijadwalkan!",
            "Sesi belum dijadwalkan. Gunakan waktu mendatang, durasi 15–240 menit, dan tautan meeting yang valid untuk barter yang diterima.");
        return saved ? "redirect:/sessions" : "redirect:/barter/" + barterId;
    }

    @PostMapping("/sessions/{id}/reschedule")
    public String rescheduleSession(@PathVariable UUID id,
                                    @RequestParam("scheduledAt") String scheduledAtStr,
                                    @RequestParam(defaultValue = "60") int durationMinutes,
                                    @RequestParam int timezoneOffset, RedirectAttributes ra) {
        UUID uid = CurrentUser.id();
        formAction(() -> {
            LearningSession s = sessionService.viewFor(uid, id);
            SessionCreateRequest req = sessionRequest(s.getBarterRequest().getId(), scheduledAtStr,
                durationMinutes, timezoneOffset, null);
            sessionService.reschedule(uid, id, req.scheduledAt(), req.durationMinutes());
        }, ra, "Jadwal sesi berhasil diperbarui.",
            "Jadwal tidak dapat diubah. Gunakan waktu mendatang dan durasi 15–240 menit untuk sesi yang masih dijadwalkan.");
        return "redirect:/sessions/" + id;
    }

    private SessionCreateRequest sessionRequest(UUID barterId, String localTime, int durationMinutes,
                                                int timezoneOffset, String externalUrl) {
        if (timezoneOffset < -1080 || timezoneOffset > 1080) throw new ValidationException("Zona waktu tidak valid");
        Instant at;
        try {
            at = LocalDateTime.parse(localTime).toInstant(ZoneOffset.ofTotalSeconds(-timezoneOffset * 60));
        } catch (DateTimeException e) {
            throw new ValidationException("Waktu tidak valid");
        }
        SessionCreateRequest req = new SessionCreateRequest(barterId, at, durationMinutes, externalUrl);
        validate(req);
        return req;
    }

    private String externalMeetingUrl(String provider, String url) {
        if ("JITSI".equals(provider)) return null;
        if (!"EXTERNAL".equals(provider) || url == null || url.isBlank()) {
            throw new ValidationException("Pilih penyedia dan tautan meeting yang valid");
        }
        String trimmed = url.trim();
        try {
            URI uri = new URI(trimmed);
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null || trimmed.length() > 2048) {
                throw new ValidationException("Tautan meeting tidak valid");
            }
        } catch (URISyntaxException e) {
            throw new ValidationException("Tautan meeting tidak valid");
        }
        return trimmed;
    }

    @PostMapping("/sessions/{id}/start")
    public String startSession(@PathVariable UUID id, RedirectAttributes ra) {
        return changeSessionStatus(id, SessionStatus.ONGOING, ra, "Sesi belajar dimulai.");
    }

    @PostMapping("/sessions/{id}/complete")
    public String completeSession(@PathVariable UUID id, RedirectAttributes ra) {
        return changeSessionStatus(id, SessionStatus.COMPLETED, ra, "Sesi selesai! Jangan lupa berikan ulasan.");
    }

    @PostMapping("/sessions/{id}/cancel")
    public String cancelSession(@PathVariable UUID id, RedirectAttributes ra) {
        return changeSessionStatus(id, SessionStatus.CANCELLED, ra, "Sesi belajar dibatalkan.");
    }

    private String changeSessionStatus(UUID id, SessionStatus status, RedirectAttributes ra, String success) {
        UUID uid = CurrentUser.id();
        formAction(() -> sessionService.setStatus(uid, id, status), ra, success,
            "Status sesi tidak dapat diubah. Periksa peserta, status barter, dan waktu sesi.");
        return "redirect:/sessions/" + id;
    }

    @PostMapping("/reviews/session/{id}")
    public String submitReview(@PathVariable UUID id, @RequestParam("rating") int rating,
                               @RequestParam(value = "comment", required = false) String comment,
                               RedirectAttributes ra) {
        UUID uid = CurrentUser.id();
        formAction(() -> {
            // Check before narrowing to short: large forged values must not wrap into 1..5.
            if (rating < 1 || rating > 5) throw new ValidationException("Rating harus 1–5");
            ReviewCreateRequest req = new ReviewCreateRequest(id, (short) rating, comment);
            validate(req);
            reviewService.create(uid, req);
        }, ra, "Terima kasih atas ulasan Anda!",
            "Ulasan tidak dapat dikirim. Pilih rating 1–5 dan komentar maksimal 2000 karakter untuk sesi selesai yang belum diulas.");
        return "redirect:/sessions/" + id;
    }

    // Portfolios

    private List<Portfolio> visiblePortfolios(UUID ownerId) {
        UUID uid = currentUserId();
        boolean privileged = ownerId.equals(uid) || (uid != null && CurrentUser.isAdmin());
        return portfolioService.forUser(ownerId).stream()
            .filter(p -> privileged || p.getVisibility() == PortfolioVisibility.PUBLIC).toList();
    }

    @GetMapping("/portfolio")
    public String portfolio(Model model) {
        model.addAttribute("portfolios", visiblePortfolios(CurrentUser.id()));
        model.addAttribute("portfolioOwner", null);
        return "portfolio/gallery";
    }

    @GetMapping("/portfolio/user/{id}")
    public String userPortfolio(@PathVariable UUID id, Model model) {
        model.addAttribute("portfolioOwner", requireUser(id));
        model.addAttribute("portfolios", visiblePortfolios(id));
        return "portfolio/gallery";
    }

    @GetMapping("/portfolio/upload")
    public String portfolioUpload(Model model) {
        model.addAttribute("portfolioDto", new PortfolioCreateDto());
        return "portfolio/upload";
    }

    @PostMapping("/portfolio/upload")
    public String portfolioUploadPost(@RequestParam(value = "file", required = false) MultipartFile file,
                                      @Valid @ModelAttribute("portfolioDto") PortfolioCreateDto dto,
                                      BindingResult errors, Model model, RedirectAttributes ra) {
        UUID uid = CurrentUser.id();
        validateTitle(dto.getTitle(), errors);
        if (!"PUBLIC".equals(dto.getVisibility()) && !"PRIVATE".equals(dto.getVisibility())) {
            errors.rejectValue("visibility", "invalid", "Pilih visibilitas publik atau privat.");
        }
        if (file == null || file.isEmpty()) {
            errors.reject("file.required", "File portofolio wajib dipilih.");
            return "portfolio/upload";
        }
        if (errors.hasErrors()) return "portfolio/upload";
        try {
            portfolioService.upload(uid,
                new PortfolioService.NewPortfolio(dto.getTitle(), dto.getDescription(), PortfolioVisibility.valueOf(dto.getVisibility())),
                file.getOriginalFilename(), file.getContentType(), file.getBytes());
            ra.addFlashAttribute("success", "Portofolio berhasil diunggah!");
            return "redirect:/portfolio";
        } catch (ValidationException | ForbiddenException | NotFoundException | UnauthorizedException
                 | jakarta.validation.ValidationException | IOException e) {
            model.addAttribute("error", "Unggah belum berhasil. Periksa judul, jenis, dan ukuran berkas, lalu pilih kembali berkas Anda.");
            return "portfolio/upload";
        }
    }

    private void validateTitle(String title, BindingResult errors) {
        if (title == null || title.isBlank() || title.trim().length() > 150) {
            errors.rejectValue("title", "invalid", "Judul wajib diisi, maksimal 150 karakter.");
        }
    }

    @GetMapping("/portfolio/{id}/edit")
    public String portfolioEdit(@PathVariable UUID id, Model model) {
        model.addAttribute("portfolio", portfolioService.requireOwned(CurrentUser.id(), id));
        return "portfolio/edit";
    }

    @PostMapping("/portfolio/{id}/edit")
    public String portfolioEditPost(@PathVariable UUID id, @RequestParam String title,
                                    @RequestParam(required = false) String description,
                                    @RequestParam PortfolioVisibility visibility, RedirectAttributes ra) {
        UUID uid = CurrentUser.id();
        boolean saved = formAction(() -> portfolioService.update(uid, id, title, description, visibility), ra,
            "Perubahan portofolio tersimpan.", "Perubahan tidak dapat disimpan. Periksa judul dan kepemilikan portofolio.");
        return saved ? "redirect:/portfolio" : "redirect:/portfolio/" + id + "/edit";
    }

    @PostMapping("/portfolio/{id}/delete")
    public String deletePortfolio(@PathVariable UUID id, RedirectAttributes ra) {
        UUID uid = CurrentUser.id();
        formAction(() -> portfolioService.delete(uid, id), ra,
            "Portofolio berhasil dihapus!", "Portofolio tidak dapat dihapus. Periksa kepemilikan dan ketersediaannya.");
        return "redirect:/portfolio";
    }

    // Notifications

    @GetMapping("/notifications")
    public String notifications(Model model) {
        model.addAttribute("notifications", notificationService.inbox(CurrentUser.id()));
        return "notification/list";
    }

    @PostMapping("/notifications/{id}/read")
    public String markNotificationRead(@PathVariable UUID id, RedirectAttributes ra) {
        UUID uid = CurrentUser.id();
        formAction(() -> notificationService.markRead(uid, id), ra,
            "Notifikasi ditandai dibaca.", "Notifikasi tidak dapat ditandai dibaca.");
        return "redirect:/notifications";
    }

    @PostMapping("/notifications/read-all")
    public String markAllNotificationsRead(RedirectAttributes ra) {
        UUID uid = CurrentUser.id();
        formAction(() -> notificationService.inbox(uid).stream().filter(n -> !n.isRead())
            .forEach(n -> notificationService.markRead(uid, n.getId())), ra,
            "Semua notifikasi ditandai dibaca.", "Sebagian notifikasi belum dapat ditandai dibaca. Silakan coba kembali.");
        return "redirect:/notifications";
    }

    // Profile & skills

    @GetMapping("/profile")
    public String profile(Model model) {
        return viewUserProfile(CurrentUser.id(), model);
    }

    @GetMapping("/profile/{id}")
    public String viewUserProfile(@PathVariable UUID id, Model model) {
        model.addAttribute("user", requireUser(id));
        model.addAttribute("isSelf", id.equals(currentUserId()));
        model.addAttribute("offerSkills", userService.listSkills(id, SkillType.OFFER));
        model.addAttribute("wantSkills", userService.listSkills(id, SkillType.WANT));
        // Only this filtered list reaches the template, including its displayed count.
        model.addAttribute("portfolios", visiblePortfolios(id));
        addReviews(model, id);
        return "profile/view";
    }

    private void addReviews(Model model, UUID userId) {
        model.addAttribute("reviews", reviewService.forUser(userId).stream()
            .map(rv -> new ReviewRow(rv.getReviewer().getFullName(), rv.getRating(), rv.getComment(), rv.getCreatedAt())).toList());
        var reputation = userService.reputation(userId);
        model.addAttribute("ratingAvg", reputation.averageRating());
        model.addAttribute("ratingCount", reputation.reviewCount());
    }

    @GetMapping("/profile/skills")
    public String profileSkills(Model model) {
        populateSkills(model);
        model.addAttribute("userSkillDto", new UserSkillDto());
        return "profile/skills";
    }

    private void populateSkills(Model model) {
        UUID uid = CurrentUser.id();
        model.addAttribute("offerSkills", userService.listSkills(uid, SkillType.OFFER));
        model.addAttribute("wantSkills", userService.listSkills(uid, SkillType.WANT));
        model.addAttribute("categories", categoryRepository.findAll());
        model.addAttribute("skills", skillRepository.findAllWithCategory());
    }

    @PostMapping("/profile/skills")
    public String addSkill(@Valid @ModelAttribute("userSkillDto") UserSkillDto dto, BindingResult errors,
                           Model model, RedirectAttributes ra) {
        UUID uid = CurrentUser.id();
        if (dto.getSkillId() == null && (dto.getSkillName() == null || dto.getSkillName().isBlank())) {
            errors.rejectValue("skillName", "required", "Pilih keahlian dari katalog atau tulis namanya.");
        }
        if (errors.hasErrors()) {
            populateSkills(model);
            return "profile/skills";
        }
        try {
            SkillType type = enumValue(SkillType.class, dto.getType());
            SkillLevel level = dto.getLevel() == null || dto.getLevel().isBlank()
                ? SkillLevel.BEGINNER : enumValue(SkillLevel.class, dto.getLevel());
            userService.addSkill(uid, dto.getSkillId(), dto.getSkillName(), type, level, dto.getNotes());
            ra.addFlashAttribute("success", "Keahlian ditambahkan ke daftar kamu.");
            return "redirect:/profile/skills";
        } catch (ValidationException | ForbiddenException | NotFoundException | UnauthorizedException
                 | jakarta.validation.ValidationException e) {
            model.addAttribute("error", "Keahlian tidak dapat ditambahkan. Periksa nama, tipe, level, dan keahlian yang sudah terdaftar.");
            populateSkills(model);
            return "profile/skills";
        }
    }

    private <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        if (value == null) throw new ValidationException("Pilihan wajib diisi");
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Pilihan tidak valid");
        }
    }

    @PostMapping("/profile/skills/{id}/delete")
    public String deleteSkill(@PathVariable UUID id, RedirectAttributes ra) {
        UUID uid = CurrentUser.id();
        formAction(() -> userService.removeSkill(uid, id), ra,
            "Keahlian dihapus.", "Keahlian tidak dapat dihapus. Periksa kepemilikan dan apakah masih digunakan.");
        return "redirect:/profile/skills";
    }

    @GetMapping("/profile/edit")
    public String profileEdit(Model model) {
        User user = requireUser(CurrentUser.id());
        model.addAttribute("profileUpdateDto", new ProfileUpdateDto(user.getFullName(), user.getBio(), user.getCampus()));
        return "profile/edit";
    }

    @PostMapping("/profile/edit")
    public String updateProfile(@Valid @ModelAttribute("profileUpdateDto") ProfileUpdateDto dto,
                                BindingResult errors, Model model) {
        UUID uid = CurrentUser.id();
        if (dto.getFullName() == null || dto.getFullName().isBlank() || dto.getFullName().trim().length() > 120) {
            errors.rejectValue("fullName", "invalid", "Nama wajib diisi, maksimal 120 karakter.");
        }
        if (dto.getCampus() != null && dto.getCampus().trim().length() > 150) {
            errors.rejectValue("campus", "invalid", "Nama kampus maksimal 150 karakter.");
        }
        if (errors.hasErrors()) return "profile/edit";
        try {
            userService.updateProfile(uid, uid, dto.getFullName(), dto.getBio(), dto.getCampus());
            return "redirect:/profile?updated";
        } catch (ValidationException | ForbiddenException | NotFoundException | UnauthorizedException
                 | jakarta.validation.ValidationException e) {
            model.addAttribute("error", "Profil belum dapat disimpan. Periksa data profil dan coba kembali.");
            return "profile/edit";
        }
    }

    @PostMapping("/profile/avatar")
    public String uploadProfileAvatar(@RequestParam("avatarFile") MultipartFile file,
                                      RedirectAttributes ra) {
        try {
            profileMedia.uploadAvatar(CurrentUser.id(), file);
            ra.addFlashAttribute("success", "Foto profil berhasil diperbarui.");
        } catch (ValidationException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/profile/edit";
    }

    @PostMapping("/profile/banner")
    public String uploadProfileBanner(@RequestParam("bannerFile") MultipartFile file,
                                      RedirectAttributes ra) {
        try {
            profileMedia.uploadBanner(CurrentUser.id(), file);
            ra.addFlashAttribute("success", "Foto sampul berhasil diperbarui.");
        } catch (ValidationException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/profile/edit";
    }

    // Student reports use the existing API request and AdminService.createReport contract.

    @PostMapping("/reports")
    public String createReport(@Valid @ModelAttribute("reportForm") CreateReportRequest req,
                               BindingResult errors, RedirectAttributes ra) {
        UUID uid = CurrentUser.id();
        String destination = req.targetType() == ReportTargetType.USER && req.targetId() != null
            ? "redirect:/profile/" + req.targetId() : "redirect:/portfolio";
        if (errors.hasErrors()) {
            ra.addFlashAttribute("error", "Pilih sasaran laporan dan isi alasan laporan.");
            return destination;
        }
        formAction(() -> adminService.createReport(uid, req.targetType(), req.targetId(), req.reason()), ra,
            "Laporan berhasil dikirim untuk ditinjau.", "Laporan tidak dapat dikirim. Periksa sasaran dan alasannya.");
        return destination;
    }

    // Admin views (ADMIN authorization is enforced by the web security chain).

    @GetMapping({"/admin", "/admin/dashboard"})
    public String adminDashboard(Model model) {
        Map<String, Long> summary = adminService.dashboardSummary();
        model.addAttribute("totalUsers", summary.get("users"));
        model.addAttribute("totalPortfolios", summary.get("portfolios"));
        model.addAttribute("totalBarters", summary.get("barterRequests"));
        model.addAttribute("totalSessions", sessionRepository.count());
        model.addAttribute("openReports", summary.get("openReports"));
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

    private String changeAccountStatus(UUID targetId, AccountStatus status, RedirectAttributes ra, String success) {
        UUID uid = CurrentUser.id();
        formAction(() -> adminService.setAccountStatus(uid, targetId, status), ra,
            success, "Status akun tidak dapat diubah. Periksa hak akses dan akun sasaran.");
        return "redirect:/admin/users";
    }

    @GetMapping("/admin/reports")
    public String adminReports(@RequestParam(required = false) ReportStatus status, Model model) {
        model.addAttribute("reports", reportRows(status == null
            ? adminService.pendingReports(50) : adminService.reportsByStatus(status)));
        model.addAttribute("activeStatus", status);
        return "admin/reports";
    }

    @PostMapping("/admin/reports/{id}/review")
    public String reviewReport(@PathVariable UUID id, @RequestParam ReportStatus decision,
                               @RequestParam(defaultValue = "false") boolean hidePortfolio,
                               @RequestParam(defaultValue = "false") boolean suspendUser, RedirectAttributes ra) {
        UUID uid = CurrentUser.id();
        formAction(() -> adminService.reviewReport(uid, id, decision, hidePortfolio, suspendUser), ra,
            decision == ReportStatus.DISMISSED ? "Laporan diabaikan." : "Laporan selesai ditindak.",
            "Laporan tidak dapat diproses. Periksa keputusan dan ketersediaan laporan.");
        return "redirect:/admin/reports";
    }

    @GetMapping("/admin/categories")
    public String adminCategories(Model model) {
        model.addAttribute("categories", adminService.categoriesWithSkills());
        return "admin/categories";
    }

    @PostMapping("/admin/categories")
    public String addCategory(@RequestParam String name, @RequestParam(required = false) String description,
                              RedirectAttributes ra) {
        formAction(() -> adminService.upsertCategory(name, description), ra,
            "Kategori tersimpan.", "Kategori tidak dapat disimpan. Periksa nama kategori.");
        return "redirect:/admin/categories";
    }

    @PostMapping("/admin/categories/{id}/skills")
    public String addCategorySkill(@PathVariable UUID id, @RequestParam String name,
                                   @RequestParam(required = false) String description, RedirectAttributes ra) {
        formAction(() -> adminService.upsertSkill(name, id, description), ra,
            "Keahlian tersimpan.", "Keahlian tidak dapat disimpan. Periksa nama dan kategorinya.");
        return "redirect:/admin/categories";
    }

    /** Materialize rows using the existing reporter fetch query; templates must not load lazy relations. */
    private List<ReportRow> reportRows(List<Report> reports) {
        return reports.stream().map(r -> {
            if (r.getTargetType() == ReportTargetType.USER) {
                // A moderated target may legitimately have been deleted since the report was filed.
                User target = userRepository.findById(r.getTargetId()).orElse(null);
                return new ReportRow(r.getId(), r.getReason(), r.getReporter().getFullName(), r.getStatus().name(),
                    "Pengguna", target == null ? "Sudah terhapus" : target.getFullName(),
                    target == null ? null : "/profile/" + target.getId(), r.getCreatedAt());
            }
            Portfolio target = portfolioRepository.findById(r.getTargetId()).orElse(null);
            return new ReportRow(r.getId(), r.getReason(), r.getReporter().getFullName(), r.getStatus().name(),
                "Portofolio", target == null ? "Sudah terhapus" : target.getTitle(),
                target == null ? null : "/web/portfolios/" + target.getId() + "/content", r.getCreatedAt());
        }).toList();
    }

    private <T> void validate(T request) {
        if (!validator.validate(request).isEmpty()) throw new ValidationException("Data formulir tidak valid");
    }

    /** Only expected business failures are converted into safe form feedback. Unexpected failures propagate. */
    private boolean formAction(Runnable action, RedirectAttributes ra, String success, String failure) {
        try {
            action.run();
            ra.addFlashAttribute("success", success);
            return true;
        } catch (ValidationException | ForbiddenException | NotFoundException | UnauthorizedException
                 | jakarta.validation.ValidationException e) {
            ra.addFlashAttribute("error", failure);
            return false;
        }
    }

    /** Also accepts POST when dispatched by the access-denied handler. */
    @RequestMapping("/access-denied")
    public String denied() {
        return "denied";
    }
}
