package com.gakbisa.controller;

import com.gakbisa.service.PortfolioService;
import com.gakbisa.service.SkillService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.util.UUID;

/** Halaman publik: landing page dan katalog portofolio (hanya yang PUBLIC). */
@Controller
public class HomeController extends BaseController {

    private final PortfolioService portfolioService;
    private final SkillService skillService;

    public HomeController(PortfolioService portfolioService, SkillService skillService) {
        this.portfolioService = portfolioService;
        this.skillService = skillService;
    }

    @GetMapping("/")
    public String home(Principal principal) {
        return principal != null ? "redirect:/dashboard" : "home";
    }

    @GetMapping("/catalog")
    public String catalog(@RequestParam(required = false) String keyword,
                          @RequestParam(required = false) UUID categoryId,
                          @RequestParam(defaultValue = "0") int page,
                          Model model) {
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        model.addAttribute("portfolios",
                portfolioService.publicCatalog(kw, categoryId, PageRequest.of(safePage(page), PAGE_SIZE)));
        model.addAttribute("categories", skillService.listCategories());
        model.addAttribute("keyword", kw);
        model.addAttribute("categoryId", categoryId);
        return "catalog";
    }
}
