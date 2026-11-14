package com.gakbisa.service;

import com.gakbisa.dto.Forms.CategoryForm;
import com.gakbisa.dto.Forms.ReportDecisionForm;
import com.gakbisa.dto.Forms.SkillForm;
import com.gakbisa.dto.Views.DashboardStats;
import com.gakbisa.dto.Views.PortfolioView;
import com.gakbisa.dto.Views.ReportView;
import com.gakbisa.dto.Views.UserAdminView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface AdminService {
    DashboardStats dashboard();

    void createCategory(CategoryForm form);
    void updateCategory(UUID categoryId, CategoryForm form);
    void deleteCategory(UUID categoryId);
    void createSkill(UUID categoryId, SkillForm form);
    void deleteSkill(UUID skillId);

    Page<UserAdminView> listUsers(Pageable pageable);
    /** status: ACTIVE / SUSPENDED. Admin tidak boleh men-suspend dirinya sendiri. */
    void setUserStatus(String adminEmail, UUID userId, String status);

    /** status boleh null = semua. */
    Page<ReportView> listReports(String status, Pageable pageable);
    void decideReport(String adminEmail, UUID reportId, ReportDecisionForm form);

    Page<PortfolioView> listPortfolios(Pageable pageable);
    void setPortfolioHidden(String adminEmail, UUID portfolioId, boolean hidden);
}
