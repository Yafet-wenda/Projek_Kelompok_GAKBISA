package com.gakbisa.backend.dto;

import java.time.Instant;

/**
 * Baris ulasan di halaman profil. Kelas biasa dengan getter karena Thymeleaf
 * membaca properti lewat getter, dan nama pengulasnya sudah dimuat di repository
 * (open-in-view dimatikan).
 */
public class ReviewRow {

    private final String reviewerName;
    private final int rating;
    private final String comment;
    private final Instant createdAt;

    public ReviewRow(String reviewerName, int rating, String comment, Instant createdAt) {
        this.reviewerName = reviewerName;
        this.rating = rating;
        this.comment = comment;
        this.createdAt = createdAt;
    }

    public String getReviewerName() { return reviewerName; }

    public int getRating() { return rating; }

    public String getComment() { return comment; }

    public Instant getCreatedAt() { return createdAt; }
}
