package com.gakbisa.backend.controller;

import com.gakbisa.backend.service.MatchingService;
import com.gakbisa.backend.domain.SkillLevel;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/matching")
public class MatchingController {

    private final MatchingService matching;

    public MatchingController(MatchingService matching) {
        this.matching = matching;
    }

    /** Rekomendasi partner + skor + alasan (indikator keberhasilan no.4). */
    @GetMapping("/candidates")
    public List<MatchingService.Candidate> candidates(@RequestParam(defaultValue = "10") int limit) {
        return matching.recommend(CurrentUser.id(), limit);
    }

    @GetMapping("/search")
    public Page<MatchingService.Candidate> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) SkillLevel level,
            @RequestParam(required = false) String campus,
            @RequestParam(required = false) String studyProgram,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return matching.search(CurrentUser.id(), new MatchingService.SearchFilters(
            keyword, categoryId, level, campus, studyProgram), Math.max(0, page), Math.min(50, Math.max(1, size)));
    }
}
