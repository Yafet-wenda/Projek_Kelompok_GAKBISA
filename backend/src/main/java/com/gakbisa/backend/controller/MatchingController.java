package com.gakbisa.backend.controller;

import com.gakbisa.backend.service.MatchingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
}
