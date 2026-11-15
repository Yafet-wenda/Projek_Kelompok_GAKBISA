package com.gakbisa.controller;

import com.gakbisa.dto.Forms.PortfolioForm;
import com.gakbisa.dto.Views.FileContent;
import com.gakbisa.service.PortfolioService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.UUID;

@Controller
public class PortfolioController extends BaseController {

    private static final String PAGE = "/portfolios";

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @GetMapping("/portfolios")
    public String mine(Principal principal, Model model) {
        model.addAttribute("portfolios", portfolioService.listMine(principal.getName()));
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new PortfolioForm());
        }
        return "portfolio/list";
    }

    @PostMapping("/portfolios")
    public String upload(@Valid @ModelAttribute("form") PortfolioForm form, BindingResult result,
                         @RequestParam("file") MultipartFile file,
                         Principal principal, RedirectAttributes ra) {
        if (result.hasErrors()) {
            ra.addFlashAttribute("error", firstError(result));
            return "redirect:" + PAGE;
        }
        if (file == null || file.isEmpty()) {
            ra.addFlashAttribute("error", "File portofolio wajib dipilih");
            return "redirect:" + PAGE;
        }
        return perform(ra, "Portofolio berhasil diunggah", PAGE,
                () -> portfolioService.upload(principal.getName(), form, file));
    }

    @PostMapping("/portfolios/{id}/delete")
    public String delete(@PathVariable UUID id, Principal principal, RedirectAttributes ra) {
        return perform(ra, "Portofolio berhasil dihapus", PAGE,
                () -> portfolioService.delete(principal.getName(), id));
    }

    /**
     * Mengalirkan file dari Supabase Storage lewat backend (bucket private).
     * Hak akses dicek di service, jadi service key tidak pernah sampai ke browser.
     */
    @GetMapping("/portfolios/{id}/file")
    public ResponseEntity<Resource> file(@PathVariable UUID id, Principal principal) {
        FileContent f = portfolioService.loadFile(principal != null ? principal.getName() : null, id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.mimeType()))
                .contentLength(f.size())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(f.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noCache().cachePrivate())
                .body(f.resource());
    }
}
