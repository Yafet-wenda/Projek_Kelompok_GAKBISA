package com.gakbisa.controller;

import com.gakbisa.dto.Forms.ReviewForm;
import com.gakbisa.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.UUID;

@Controller
public class ReviewController extends BaseController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping("/sessions/{sessionId}/reviews")
    public String submit(@PathVariable UUID sessionId,
                         @Valid @ModelAttribute("reviewForm") ReviewForm form, BindingResult result,
                         Principal principal, RedirectAttributes ra) {
        String back = "/sessions/" + sessionId;
        if (result.hasErrors()) {
            ra.addFlashAttribute("error", firstError(result));
            return "redirect:" + back;
        }
        return perform(ra, "Terima kasih, ulasan berhasil dikirim", back,
                () -> reviewService.submit(principal.getName(), sessionId, form));
    }
}
