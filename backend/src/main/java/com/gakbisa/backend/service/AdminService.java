package com.gakbisa.backend.service;

import com.gakbisa.backend.config.AppExceptions.NotFoundException;
import com.gakbisa.backend.config.AppExceptions.ValidationException;
import com.gakbisa.backend.domain.AccountStatus;
import com.gakbisa.backend.domain.Report;
import com.gakbisa.backend.domain.ReportStatus;
import com.gakbisa.backend.domain.ReportTargetType;
import com.gakbisa.backend.domain.Skill;
import com.gakbisa.backend.domain.SkillCategory;
import com.gakbisa.backend.domain.User;
import com.gakbisa.backend.domain.UserRole;
import com.gakbisa.backend.repository.BarterRequestRepository;
import com.gakbisa.backend.repository.LearningSessionRepository;
import com.gakbisa.backend.repository.PortfolioRepository;
import com.gakbisa.backend.repository.ReportRepository;
import com.gakbisa.backend.repository.SkillCategoryRepository;
import com.gakbisa.backend.repository.SkillRepository;
import com.gakbisa.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Fungsi panel admin (proposal §4.2): kategori, moderasi, laporan, ringkasan sistem. */
@Service
public class AdminService {

    private final SkillCategoryRepository categories;
    private final SkillRepository skills;
    private final UserRepository users;
    private final ReportRepository reports;
    private final PortfolioRepository portfolios;
    private final BarterRequestRepository barter;
    private final LearningSessionRepository sessions;
    private final NotificationService notifications;

    public AdminService(SkillCategoryRepository categories, SkillRepository skills, UserRepository users,
                        ReportRepository reports, PortfolioRepository portfolios, BarterRequestRepository barter,
                        LearningSessionRepository sessions, NotificationService notifications) {
        this.categories = categories;
        this.skills = skills;
        this.users = users;
        this.reports = reports;
        this.portfolios = portfolios;
        this.barter = barter;
        this.sessions = sessions;
        this.notifications = notifications;
    }

    // ---- Kategori & keahlian ----

    @Transactional
    public SkillCategory upsertCategory(String name, String description) {
        if (name == null || name.isBlank()) throw new ValidationException("Nama kategori wajib diisi");
        SkillCategory c = categories.findByNameIgnoreCase(name.trim())
            .orElseGet(SkillCategory::new);
        c.setName(name.trim());
        c.setDescription(description);
        return categories.save(c);
    }

    @Transactional
    public Skill upsertSkill(String name, UUID categoryId, String description) {
        if (name == null || name.isBlank()) throw new ValidationException("Nama keahlian wajib diisi");
        SkillCategory cat = categories.findById(categoryId)
            .orElseThrow(() -> new NotFoundException("Kategori tidak ditemukan"));
        Skill s = skills.findByNameIgnoreCase(name.trim()).orElseGet(Skill::new);
        s.setName(name.trim());
        s.setCategory(cat);
        s.setDescription(description);
        return skills.save(s);
    }

    @Transactional(readOnly = true)
    public List<SkillCategory> listCategories() {
        return categories.findAll();
    }

    // ---- Laporan ----

    @Transactional
    public Report createReport(UUID reporterId, ReportTargetType targetType, UUID targetId, String reason) {
        User reporter = users.findById(reporterId).orElseThrow(() -> new NotFoundException("Pelapor tidak ditemukan"));
        if (reason == null || reason.isBlank()) throw new ValidationException("Alasan laporan wajib diisi");
        if (targetType == ReportTargetType.PORTFOLIO) {
            if (!portfolios.existsById(targetId)) throw new NotFoundException("Portofolio yang dilaporkan tidak ditemukan");
        } else {
            if (!users.existsById(targetId)) throw new NotFoundException("Pengguna yang dilaporkan tidak ditemukan");
            if (targetId.equals(reporterId)) throw new ValidationException("Tidak dapat melaporkan diri sendiri");
        }
        Report r = new Report();
        r.setReporter(reporter);
        r.setTargetType(targetType);
        r.setTargetId(targetId);
        r.setReason(reason.trim());
        r.setStatus(ReportStatus.OPEN);
        return reports.save(r);
    }

    @Transactional
    public Report reviewReport(UUID adminId, UUID reportId, ReportStatus decision, boolean hidePortfolio, boolean suspendUser) {
        Report r = reports.findById(reportId).orElseThrow(() -> new NotFoundException("Laporan tidak ditemukan"));
        if (decision == ReportStatus.OPEN) throw new ValidationException("Pilih keputusan REVIEWING/RESOLVED/DISMISSED");
        r.setStatus(decision);
        Report saved = reports.save(r);

        if (decision == ReportStatus.RESOLVED) {
            if (hidePortfolio && r.getTargetType() == ReportTargetType.PORTFOLIO) {
                portfolios.findById(r.getTargetId()).ifPresent(p -> {
                    p.setVisibility(com.gakbisa.backend.domain.PortfolioVisibility.PRIVATE);
                    portfolios.save(p);
                    notifications.push(p.getUser(), NotificationService.TYPE_MODERATION, "Karya disembunyikan",
                        "Portofolio '" + p.getTitle() + "' diubah menjadi privat oleh moderator.");
                });
            }
            if (suspendUser && r.getTargetType() == ReportTargetType.USER) {
                users.findById(r.getTargetId()).ifPresent(u -> {
                    u.setStatus(AccountStatus.SUSPENDED);
                    users.save(u);
                    notifications.push(u, NotificationService.TYPE_MODERATION, "Akun dibatasi",
                        "Akun kamu dibatasi berdasarkan keputusan moderasi.");
                });
            }
        }
        notifications.push(r.getReporter(), NotificationService.TYPE_MODERATION, "Laporan ditinjau",
            "Laporanmu berstatus " + decision + " (peninjau: admin " + adminId + ").");
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Report> reportsByStatus(ReportStatus status) {
        return reports.findByStatusOrderByCreatedAtDesc(status);
    }

    // ---- Pengguna ----

    @Transactional(readOnly = true)
    public List<User> listUsers() {
        return users.findAll();
    }

    @Transactional
    public User setAccountStatus(UUID adminId, UUID targetUserId, AccountStatus status) {
        User admin = users.findById(adminId).orElseThrow(() -> new NotFoundException("Admin tidak ditemukan"));
        if (admin.getRole() != UserRole.ADMIN) throw new com.gakbisa.backend.config.AppExceptions.ForbiddenException("Akses admin diperlukan");
        User target = users.findById(targetUserId).orElseThrow(() -> new NotFoundException("Pengguna tidak ditemukan"));
        if (target.getId().equals(adminId)) throw new ValidationException("Admin tidak dapat membatasi akun sendiri");
        target.setStatus(status);
        return users.save(target);
    }

    // ---- Ringkasan dashboard ----

    @Transactional(readOnly = true)
    public Map<String, Long> dashboardSummary() {
        Map<String, Long> m = new LinkedHashMap<>();
        m.put("users", users.count());
        m.put("students", users.countByRole(UserRole.STUDENT));
        m.put("portfolios", portfolios.count());
        m.put("barterRequests", barter.count());
        m.put("sessionsCompleted", sessions.countByStatus(com.gakbisa.backend.domain.SessionStatus.COMPLETED));
        m.put("openReports", reports.countByStatus(ReportStatus.OPEN) + reports.countByStatus(ReportStatus.REVIEWING));
        return m;
    }
}
