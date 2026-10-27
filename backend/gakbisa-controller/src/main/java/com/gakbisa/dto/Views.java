package com.gakbisa.dto;

import org.springframework.core.io.Resource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Model read-only yang dikirim service ke controller/template. */
public final class Views {
    private Views() {}

    public record SkillView(UUID id, UUID categoryId, String name, String description) {}

    public record CategoryView(UUID id, String name, String description, List<SkillView> skills) {}

    public record UserSkillView(UUID id, UUID skillId, String skillName, String categoryName,
                                String type, String level, String notes) {}

    public record PortfolioView(UUID id, UUID ownerId, String ownerName, String title, String description,
                                String fileName, String mimeType, long fileSize, String visibility,
                                boolean hidden, LocalDateTime createdAt) {}

    /** Isi file dari Supabase Storage yang akan dialirkan ke browser. */
    public record FileContent(Resource resource, String fileName, String mimeType, long size) {}

    public record ProfileView(UUID id, String fullName, String email, String campus, String bio,
                              String avatarUrl, double avgRating, long reviewCount, long completedSessions,
                              List<UserSkillView> offers, List<UserSkillView> wants,
                              List<PortfolioView> portfolios) {}

    public record MatchView(UUID candidateId, String fullName, String campus, int score,
                            List<String> reasons, double avgRating) {}

    public record MatchDetailView(ProfileView candidate, int score, List<String> reasons,
                                  List<UserSkillView> myOffers, List<UserSkillView> myWants) {}

    /** Flag canXxx dihitung service berdasarkan peran pemanggil & status saat ini. */
    public record BarterView(UUID id, UUID requesterId, String requesterName, UUID targetId, String targetName,
                             String offeredSkill, String wantedSkill, String message, String status,
                             LocalDateTime createdAt, boolean canRespond, boolean canCancel,
                             boolean canSchedule, boolean canComplete) {}

    public record SessionView(UUID id, UUID barterId, String partnerName, LocalDateTime scheduledAt,
                              int durationMinutes, String status, String meetingProvider,
                              boolean canJoin, boolean canReschedule, boolean canCancel,
                              boolean canComplete, boolean canReview) {}

    public record MeetingInfo(String provider, String domain, String room, String url, String displayName) {}

    public record NotificationView(UUID id, String title, String body, String type, boolean read,
                                   LocalDateTime createdAt) {}

    public record UserAdminView(UUID id, String fullName, String email, String role, String status,
                                LocalDateTime createdAt) {}

    public record ReportView(UUID id, String reporterName, String targetType, UUID targetId, String reason,
                             String status, String adminNote, LocalDateTime createdAt) {}

    public record DashboardStats(long totalUsers, long totalPortfolios, long totalBarters,
                                 long completedSessions, long openReports) {}
}
