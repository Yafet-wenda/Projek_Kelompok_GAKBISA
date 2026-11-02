package com.gakbisa.backend.dto;

import com.gakbisa.backend.domain.User;
import java.util.List;

/**
 * DTO hasil pencocokan (skill matching) untuk Thymeleaf.
 * Menampilkan data kandidat partner belajar, skor kecocokan (maks 100 poin),
 * dan alasan rekomendasi sesuai proposal §4.3.
 */
public class MatchResult {

    private User candidate;
    private int score;
    private List<String> reasons;

    public MatchResult() {}

    public MatchResult(User candidate, int score, List<String> reasons) {
        this.candidate = candidate;
        this.score = score;
        this.reasons = reasons;
    }

    public User getCandidate() {
        return candidate;
    }

    public void setCandidate(User candidate) {
        this.candidate = candidate;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public List<String> getReasons() {
        return reasons;
    }

    public void setReasons(List<String> reasons) {
        this.reasons = reasons;
    }
}

