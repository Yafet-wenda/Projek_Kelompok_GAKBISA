package com.gakbisa.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Kumpulan form (command object) untuk binding Thymeleaf.
 * Dibuat sebagai class biasa (bukan record) agar th:field aman dipakai.
 */
public final class Forms {
    private Forms() {}

    public static class RegisterForm {
        @NotBlank(message = "Nama wajib diisi") @Size(max = 120, message = "Nama maksimal 120 karakter")
        private String fullName;
        @NotBlank(message = "Email wajib diisi") @Email(message = "Format email tidak valid") @Size(max = 254)
        private String email;
        @NotBlank(message = "Password wajib diisi") @Size(min = 8, max = 72, message = "Password 8-72 karakter")
        private String password;
        @NotBlank(message = "Konfirmasi password wajib diisi")
        private String confirmPassword;
        @Size(max = 150, message = "Kampus/prodi maksimal 150 karakter")
        private String campus;

        public String getFullName() { return fullName; }
        public void setFullName(String v) { this.fullName = v; }
        public String getEmail() { return email; }
        public void setEmail(String v) { this.email = v; }
        public String getPassword() { return password; }
        public void setPassword(String v) { this.password = v; }
        public String getConfirmPassword() { return confirmPassword; }
        public void setConfirmPassword(String v) { this.confirmPassword = v; }
        public String getCampus() { return campus; }
        public void setCampus(String v) { this.campus = v; }
    }

    public static class ProfileForm {
        @NotBlank(message = "Nama wajib diisi") @Size(max = 120)
        private String fullName;
        @Size(max = 1000, message = "Bio maksimal 1000 karakter")
        private String bio;
        @Size(max = 150)
        private String campus;

        public String getFullName() { return fullName; }
        public void setFullName(String v) { this.fullName = v; }
        public String getBio() { return bio; }
        public void setBio(String v) { this.bio = v; }
        public String getCampus() { return campus; }
        public void setCampus(String v) { this.campus = v; }
    }

    public static class UserSkillForm {
        @NotNull(message = "Keahlian wajib dipilih")
        private UUID skillId;
        @NotBlank @Pattern(regexp = "OFFER|WANT", message = "Tipe harus OFFER atau WANT")
        private String type;
        @NotBlank @Pattern(regexp = "BEGINNER|INTERMEDIATE|ADVANCED", message = "Level tidak valid")
        private String level = "BEGINNER";
        @Size(max = 1000, message = "Catatan maksimal 1000 karakter")
        private String notes;

        public UUID getSkillId() { return skillId; }
        public void setSkillId(UUID v) { this.skillId = v; }
        public String getType() { return type; }
        public void setType(String v) { this.type = v; }
        public String getLevel() { return level; }
        public void setLevel(String v) { this.level = v; }
        public String getNotes() { return notes; }
        public void setNotes(String v) { this.notes = v; }
    }

    public static class PortfolioForm {
        @NotBlank(message = "Judul wajib diisi") @Size(max = 150)
        private String title;
        @Size(max = 2000)
        private String description;
        @NotBlank @Pattern(regexp = "PUBLIC|PRIVATE", message = "Visibilitas harus PUBLIC atau PRIVATE")
        private String visibility = "PUBLIC";

        public String getTitle() { return title; }
        public void setTitle(String v) { this.title = v; }
        public String getDescription() { return description; }
        public void setDescription(String v) { this.description = v; }
        public String getVisibility() { return visibility; }
        public void setVisibility(String v) { this.visibility = v; }
    }

    public static class BarterForm {
        @NotNull(message = "Penerima wajib dipilih")
        private UUID targetUserId;
        @NotNull(message = "Pilih keahlian yang kamu tawarkan")
        private UUID offeredUserSkillId;
        @NotNull(message = "Pilih keahlian yang ingin kamu pelajari")
        private UUID wantedUserSkillId;
        @Size(max = 1000, message = "Pesan maksimal 1000 karakter")
        private String message;

        public UUID getTargetUserId() { return targetUserId; }
        public void setTargetUserId(UUID v) { this.targetUserId = v; }
        public UUID getOfferedUserSkillId() { return offeredUserSkillId; }
        public void setOfferedUserSkillId(UUID v) { this.offeredUserSkillId = v; }
        public UUID getWantedUserSkillId() { return wantedUserSkillId; }
        public void setWantedUserSkillId(UUID v) { this.wantedUserSkillId = v; }
        public String getMessage() { return message; }
        public void setMessage(String v) { this.message = v; }
    }

    public static class SessionForm {
        @NotNull(message = "Tanggal & jam wajib diisi") @Future(message = "Jadwal harus di masa depan")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        private LocalDateTime scheduledAt;
        @NotNull @Min(value = 15, message = "Durasi minimal 15 menit") @Max(value = 240, message = "Durasi maksimal 240 menit")
        private Integer durationMinutes = 60;
        @NotBlank @Pattern(regexp = "JITSI|EXTERNAL_LINK", message = "Penyedia meeting tidak valid")
        private String meetingProvider = "JITSI";
        @Size(max = 500) @Pattern(regexp = "^$|^https://.+", message = "Tautan meeting harus diawali https://")
        private String meetingUrl;

        public LocalDateTime getScheduledAt() { return scheduledAt; }
        public void setScheduledAt(LocalDateTime v) { this.scheduledAt = v; }
        public Integer getDurationMinutes() { return durationMinutes; }
        public void setDurationMinutes(Integer v) { this.durationMinutes = v; }
        public String getMeetingProvider() { return meetingProvider; }
        public void setMeetingProvider(String v) { this.meetingProvider = v; }
        public String getMeetingUrl() { return meetingUrl; }
        public void setMeetingUrl(String v) { this.meetingUrl = v; }
    }

    public static class ReviewForm {
        @NotNull(message = "Rating wajib diisi") @Min(value = 1, message = "Rating minimal 1") @Max(value = 5, message = "Rating maksimal 5")
        private Integer rating;
        @Size(max = 1000, message = "Komentar maksimal 1000 karakter")
        private String comment;

        public Integer getRating() { return rating; }
        public void setRating(Integer v) { this.rating = v; }
        public String getComment() { return comment; }
        public void setComment(String v) { this.comment = v; }
    }

    public static class ReportForm {
        @NotBlank @Pattern(regexp = "PORTFOLIO|USER", message = "Jenis laporan tidak valid")
        private String targetType;
        @NotNull
        private UUID targetId;
        @NotBlank(message = "Alasan wajib diisi") @Size(max = 1000, message = "Alasan maksimal 1000 karakter")
        private String reason;

        public String getTargetType() { return targetType; }
        public void setTargetType(String v) { this.targetType = v; }
        public UUID getTargetId() { return targetId; }
        public void setTargetId(UUID v) { this.targetId = v; }
        public String getReason() { return reason; }
        public void setReason(String v) { this.reason = v; }
    }

    public static class CategoryForm {
        @NotBlank(message = "Nama kategori wajib diisi") @Size(max = 80)
        private String name;
        @Size(max = 500)
        private String description;

        public String getName() { return name; }
        public void setName(String v) { this.name = v; }
        public String getDescription() { return description; }
        public void setDescription(String v) { this.description = v; }
    }

    public static class SkillForm {
        @NotBlank(message = "Nama keahlian wajib diisi") @Size(max = 100)
        private String name;
        @Size(max = 500)
        private String description;

        public String getName() { return name; }
        public void setName(String v) { this.name = v; }
        public String getDescription() { return description; }
        public void setDescription(String v) { this.description = v; }
    }

    public static class ReportDecisionForm {
        @NotBlank @Pattern(regexp = "OPEN|REVIEWING|RESOLVED|DISMISSED", message = "Status laporan tidak valid")
        private String status;
        @Size(max = 1000)
        private String adminNote;

        public String getStatus() { return status; }
        public void setStatus(String v) { this.status = v; }
        public String getAdminNote() { return adminNote; }
        public void setAdminNote(String v) { this.adminNote = v; }
    }
}
