package com.gakbisa.backend.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Baris tabel laporan untuk Thymeleaf (dashboard admin dan antrean moderasi).
 * Nama pelapor serta sasaran sudah diselesaikan di service/controller, karena
 * open-in-view dimatikan dan relasi LAZY tidak bisa dibaca dari template.
 * Kelas biasa dengan getter, bukan record: SpEL hanya membaca properti lewat getter.
 */
public class ReportRow {

    private final UUID id;
    private final String reason;
    private final String reporterName;
    private final String status;
    private final String targetKind;
    private final String targetName;
    private final String targetUrl;
    private final Instant createdAt;

    public ReportRow(UUID id, String reason, String reporterName, String status,
                     String targetKind, String targetName, String targetUrl, Instant createdAt) {
        this.id = id;
        this.reason = reason;
        this.reporterName = reporterName;
        this.status = status;
        this.targetKind = targetKind;
        this.targetName = targetName;
        this.targetUrl = targetUrl;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }

    public String getReason() { return reason; }

    public String getReporterName() { return reporterName; }

    public String getStatus() { return status; }

    public String getTargetKind() { return targetKind; }

    public String getTargetName() { return targetName; }

    /** Null kalau sasaran tidak punya halaman rinci (portofolio). */
    public String getTargetUrl() { return targetUrl; }

    public Instant getCreatedAt() { return createdAt; }

    public boolean isPending() {
        return "OPEN".equals(status) || "REVIEWING".equals(status);
    }
}
