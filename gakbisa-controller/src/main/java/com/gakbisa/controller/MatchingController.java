package com.gakbisa.controller;

import com.gakbisa.service.MatchingService;
import com.gakbisa.service.SkillService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.util.UUID;

/** Pencarian partner + rekomendasi skill matching (skor & alasan kecocokan). */
@Controller
public class MatchingController extends BaseController {

    private final MatchingService matchingService;
    private final SkillService skillService;

    public MatchingController(MatchingService matchingService, SkillService skillService) {
        this.matchingService = matchingService;
        this.skillService = skillService;
    }

    @GetMapping("/matching")
    public String index(@RequestParam(required = false) String keyword,
                        @RequestParam(required = false) UUID categoryId,
                        @RequestParam(defaultValue = "0") int page,
                        Principal principal, Model model) {
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        model.addAttribute("results",
                matchingService.search(principal.getName(), kw, categoryId, PageRequest.of(safePage(page), PAGE_SIZE)));
        model.addAttribute("categories", skillService.listCategories());
        model.addAttribute("keyword", kw);
        model.addAttribute("categoryId", categoryId);
        return "matching/list";
    }

    /** Detail kandidat + form kirim permintaan barter. */
    @GetMapping("/matching/{candidateId}")
    public String detail(@PathVariable UUID candidateId, Principal principal, Model model) {
        model.addAttribute("match", matchingService.detail(principal.getName(), candidateId));
        return "matching/detail";
    }
}
