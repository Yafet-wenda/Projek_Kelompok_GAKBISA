package com.gakbisa.controller;

import com.gakbisa.dto.Forms.CategoryForm;
import com.gakbisa.dto.Forms.ReportDecisionForm;
import com.gakbisa.dto.Forms.SkillForm;
import com.gakbisa.service.AdminService;
import com.gakbisa.service.SkillService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.Set;
import java.util.UUID;

/**
 * Panel admin. Dilindungi 2 lapis: /admin/** di SecurityConfig + @PreAuthorize di sini
 * (butuh @EnableMethodSecurity pada kelas konfigurasi).
 */
@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController extends BaseController {

    private static final Set<String> USER_STATUSES = Set.of("ACTIVE", "SUSPENDED");

    private final AdminService adminService;
    private final SkillService skillService;

    public AdminController(AdminService adminService, SkillService skillService) {
        this.adminService = adminService;
        this.skillService = skillService;
    }

    // ---------- Dashboard ----------
    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("stats", adminService.dashboard());
        return "admin/dashboard";
    }

    // ---------- Kategori & keahlian ----------
    @GetMapping("/categories")
    public String categories(Model model) {
        model.addAttribute("categories", skillService.listCategories());
        model.addAttribute("categoryForm", new CategoryForm());
        model.addAttribute("skillForm", new SkillForm());
        return "admin/categories";
    }

    @PostMapping("/categories")
    public String createCategory(@Valid @ModelAttribute CategoryForm form, BindingResult result,
                                 RedirectAttributes ra) {
        if (result.hasErrors()) {
            ra.addFlashAttribute("error", firstError(result));
            return "redirect:/admin/categories";
        }
        return perform(ra, "Kategori ditambahkan", "/admin/categories", () -> adminService.createCategory(form));
    }

    @PostMapping("/categories/{id}/update")
    public String updateCategory(@PathVariable UUID id, @Valid @ModelAttribute CategoryForm form,
                                 BindingResult result, RedirectAttributes ra) {
        if (result.hasErrors()) {
            ra.addFlashAttribute("error", firstError(result));
            return "redirect:/admin/categories";
        }
        return perform(ra, "Kategori diperbarui", "/admin/categories", () -> adminService.updateCategory(id, form));
    }

    @PostMapping("/categories/{id}/delete")
    public String deleteCategory(@PathVariable UUID id, RedirectAttributes ra) {
        return perform(ra, "Kategori dihapus", "/admin/categories", () -> adminService.deleteCategory(id));
    }

    @PostMapping("/categories/{id}/skills")
    public String createSkill(@PathVariable UUID id, @Valid @ModelAttribute SkillForm form,
                              BindingResult result, RedirectAttributes ra) {
        if (result.hasErrors()) {
            ra.addFlashAttribute("error", firstError(result));
            return "redirect:/admin/categories";
        }
        return perform(ra, "Keahlian ditambahkan", "/admin/categories", () -> adminService.createSkill(id, form));
    }

    @PostMapping("/skills/{id}/delete")
    public String deleteSkill(@PathVariable UUID id, RedirectAttributes ra) {
        return perform(ra, "Keahlian dihapus", "/admin/categories", () -> adminService.deleteSkill(id));
    }

    // ---------- Pengguna ----------
    @GetMapping("/users")
    public String users(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("users", adminService.listUsers(PageRequest.of(safePage(page), PAGE_SIZE)));
        return "admin/users";
    }

    @PostMapping("/users/{id}/status")
    public String setUserStatus(@PathVariable UUID id, @RequestParam String status,
                                Principal principal, RedirectAttributes ra) {
        if (!USER_STATUSES.contains(status)) {
            ra.addFlashAttribute("error", "Status pengguna tidak valid");
            return "redirect:/admin/users";
        }
        return perform(ra, "Status pengguna diperbarui", "/admin/users",
                () -> adminService.setUserStatus(principal.getName(), id, status));
    }

    // ---------- Laporan ----------
    @GetMapping("/reports")
    public String reports(@RequestParam(required = false) String status,
                          @RequestParam(defaultValue = "0") int page, Model model) {
        String st = (status == null || status.isBlank()) ? null : status;
        model.addAttribute("reports", adminService.listReports(st, PageRequest.of(safePage(page), PAGE_SIZE)));
        model.addAttribute("status", st);
        return "admin/reports";
    }

    @PostMapping("/reports/{id}")
    public String decideReport(@PathVariable UUID id, @Valid @ModelAttribute ReportDecisionForm form,
                               BindingResult result, Principal principal, RedirectAttributes ra) {
        if (result.hasErrors()) {
            ra.addFlashAttribute("error", firstError(result));
            return "redirect:/admin/reports";
        }
        return perform(ra, "Laporan diperbarui", "/admin/reports",
                () -> adminService.decideReport(principal.getName(), id, form));
    }

    // ---------- Moderasi portofolio ----------
    @GetMapping("/portfolios")
    public String portfolios(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("portfolios", adminService.listPortfolios(PageRequest.of(safePage(page), PAGE_SIZE)));
        return "admin/portfolios";
    }

    @PostMapping("/portfolios/{id}/hidden")
    public String setHidden(@PathVariable UUID id, @RequestParam boolean hidden,
                            Principal principal, RedirectAttributes ra) {
        return perform(ra, hidden ? "Portofolio disembunyikan" : "Portofolio ditampilkan kembali",
                "/admin/portfolios", () -> adminService.setPortfolioHidden(principal.getName(), id, hidden));
    }
}
