package com.gakbisa.backend.view;

import org.springframework.stereotype.Component;

/**
 * Label tampilan untuk nilai enum domain. Nama enum tetap dipakai di atribut
 * data-status (pewarnaan CSS), sedangkan teks yang dibaca pengguna berbahasa Indonesia.
 */
@Component("label")
public class Labels {

    public String status(Object value) {
        if (value == null) {
            return "-";
        }
        return switch (value.toString()) {
            case "ACTIVE" -> "Aktif";
            case "SUSPENDED" -> "Ditangguhkan";
            case "PENDING" -> "Menunggu";
            case "ACCEPTED" -> "Diterima";
            case "REJECTED" -> "Ditolak";
            case "CANCELLED" -> "Dibatalkan";
            case "SCHEDULED" -> "Terjadwal";
            case "ONGOING" -> "Berlangsung";
            case "COMPLETED" -> "Selesai";
            case "OPEN" -> "Terbuka";
            case "REVIEWING" -> "Ditinjau";
            case "RESOLVED" -> "Selesai";
            case "DISMISSED" -> "Diabaikan";
            default -> value.toString();
        };
    }

    public String level(Object value) {
        if (value == null) {
            return "";
        }
        return switch (value.toString()) {
            case "BEGINNER" -> "Pemula";
            case "INTERMEDIATE" -> "Menengah";
            case "ADVANCED" -> "Mahir";
            default -> value.toString();
        };
    }

    public String role(Object value) {
        if (value == null) {
            return "-";
        }
        return switch (value.toString()) {
            case "STUDENT" -> "Mahasiswa";
            case "ADMIN" -> "Admin";
            default -> value.toString();
        };
    }

    public String provider(Object value) {
        if (value == null) {
            return "Jitsi Meet";
        }
        return switch (value.toString()) {
            case "JITSI" -> "Jitsi Meet";
            case "EXTERNAL" -> "Tautan eksternal";
            default -> value.toString();
        };
    }

    public String visibility(Object value) {
        if (value == null) {
            return "-";
        }
        return switch (value.toString()) {
            case "PUBLIC" -> "Publik";
            case "PRIVATE" -> "Privat";
            default -> value.toString();
        };
    }
}
