package com.gakbisa.service;

import com.gakbisa.dto.Views.MatchDetailView;
import com.gakbisa.dto.Views.MatchView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface MatchingService {
    /** Tanpa keyword/categoryId = daftar rekomendasi; urut skor menurun. */
    Page<MatchView> search(String email, String keyword, UUID categoryId, Pageable pageable);
    List<MatchView> recommend(String email, int limit);
    MatchDetailView detail(String email, UUID candidateId);
}
