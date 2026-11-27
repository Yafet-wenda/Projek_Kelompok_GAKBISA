package com.gakbisa.backend.service;

import com.gakbisa.backend.domain.*;
import com.gakbisa.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminServiceTest {

    private ReportRepository reports;
    private NotificationService notifications;
    private AdminService service;
    private Report report;
    private User reporter;

    @BeforeEach
    void setup() {
        SkillCategoryRepository categories = mock(SkillCategoryRepository.class);
        SkillRepository skills = mock(SkillRepository.class);
        UserRepository users = mock(UserRepository.class);
        reports = mock(ReportRepository.class);
        PortfolioRepository portfolios = mock(PortfolioRepository.class);
        BarterRequestRepository barter = mock(BarterRequestRepository.class);
        LearningSessionRepository sessions = mock(LearningSessionRepository.class);
        notifications = mock(NotificationService.class);
        service = new AdminService(categories, skills, users, reports, portfolios, barter, sessions, notifications);

        reporter = new User();
        reporter.setId(UUID.randomUUID());
        reporter.setFullName("Pelapor");
        report = new Report();
        report.setId(UUID.randomUUID());
        report.setReporter(reporter);
        report.setTargetType(ReportTargetType.USER);
        report.setTargetId(UUID.randomUUID());
        report.setReason("Alasan laporan");
        report.setStatus(ReportStatus.OPEN);
        when(reports.findById(report.getId())).thenReturn(Optional.of(report));
        when(reports.save(report)).thenReturn(report);
    }

    @Test
    void reviewReportResolvedMenandaiLaporanDitindak() {
        Report result = service.reviewReport(UUID.randomUUID(), report.getId(), ReportStatus.RESOLVED, false, false);

        assertEquals(ReportStatus.RESOLVED, result.getStatus());
        verify(reports).save(report);
        verify(notifications).push(eq(reporter), eq(NotificationService.TYPE_MODERATION), eq("Laporan ditinjau"), contains("RESOLVED"));
    }

    @Test
    void reviewReportDismissedMenandaiLaporanDitolak() {
        Report result = service.reviewReport(UUID.randomUUID(), report.getId(), ReportStatus.DISMISSED, false, false);

        assertEquals(ReportStatus.DISMISSED, result.getStatus());
        verify(reports).save(report);
        verify(notifications).push(eq(reporter), eq(NotificationService.TYPE_MODERATION), eq("Laporan ditinjau"), contains("DISMISSED"));
    }
}
