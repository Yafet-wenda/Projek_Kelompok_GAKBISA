package com.gakbisa.backend.controller;

import com.gakbisa.backend.domain.Report;
import com.gakbisa.backend.domain.ReportStatus;
import com.gakbisa.backend.domain.ReportTargetType;
import com.gakbisa.backend.service.AdminService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ReportAdminController {

    public record CreateReportRequest(@NotNull ReportTargetType targetType, @NotNull UUID targetId,
                                      @NotBlank String reason) {}

    public record ReviewDecisionRequest(@NotNull ReportStatus decision,
                                        boolean hidePortfolio,
                                        boolean suspendUser) {}

    private final AdminService admin;

    public ReportAdminController(AdminService admin) {
        this.admin = admin;
    }

    /** Mahasiswa dapat melaporkan konten atau pengguna. */
    @PostMapping("/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@RequestBody CreateReportRequest req) {
        Report r = admin.createReport(CurrentUser.id(), req.targetType(), req.targetId(), req.reason());
        return Map.of("id", r.getId(), "status", r.getStatus().name());
    }

    // Berikutnya khusus ADMIN.
    @GetMapping("/admin/reports")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String, Object>> reports(@RequestParam(defaultValue = "OPEN") ReportStatus status) {
        return admin.reportsByStatus(status).stream()
            .map(r -> Map.<String, Object>of(
                "id", r.getId(), "targetType", r.getTargetType().name(), "targetId", r.getTargetId(),
                "reason", r.getReason(), "status", r.getStatus().name(), "createdAt", r.getCreatedAt()))
            .toList();
    }

    @PostMapping("/admin/reports/{id}/review")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> review(@PathVariable UUID id, @RequestBody ReviewDecisionRequest req) {
        Report r = admin.reviewReport(CurrentUser.id(), id, req.decision(), req.hidePortfolio(), req.suspendUser());
        return Map.of("id", r.getId(), "status", r.getStatus().name());
    }

    @PostMapping("/admin/categories")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> upsertCategory(@RequestBody Map<String, String> body) {
        var c = admin.upsertCategory(body.get("name"), body.get("description"));
        return Map.of("id", c.getId(), "name", c.getName());
    }

    @PostMapping("/admin/skills")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> upsertSkill(@RequestBody Map<String, String> body) {
        var s = admin.upsertSkill(body.get("name"), UUID.fromString(body.get("categoryId")), body.get("description"));
        return Map.of("id", s.getId(), "name", s.getName());
    }

    @GetMapping("/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String, Object>> users() {
        return admin.listUsers().stream()
            .map(u -> Map.<String, Object>of("id", u.getId(), "email", u.getEmail(),
                "fullName", u.getFullName(), "role", u.getRole().name(), "status", u.getStatus().name()))
            .toList();
    }

    @PostMapping("/admin/users/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> setAccountStatus(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        var u = admin.setAccountStatus(CurrentUser.id(), id,
            com.gakbisa.backend.domain.AccountStatus.valueOf(body.get("status")));
        return Map.of("id", u.getId(), "status", u.getStatus().name());
    }

    @GetMapping("/admin/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Long> summary() {
        return admin.dashboardSummary();
    }
}
