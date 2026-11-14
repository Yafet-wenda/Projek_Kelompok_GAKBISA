package com.gakbisa.controller;

import com.gakbisa.dto.Forms.BarterForm;
import com.gakbisa.exception.BusinessException;
import com.gakbisa.service.BarterService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.UUID;

/** Alur barter: PENDING -> ACCEPTED/REJECTED/CANCELLED -> COMPLETED. Validasi transisi ada di service. */
@Controller
public class BarterController extends BaseController {

    private static final String PAGE = "/barters";

    private final BarterService barterService;

    public BarterController(BarterService barterService) {
        this.barterService = barterService;
    }

    @GetMapping("/barters")
    public String list(Principal principal, Model model) {
        model.addAttribute("incoming", barterService.incoming(principal.getName()));
        model.addAttribute("outgoing", barterService.outgoing(principal.getName()));
        return "barter/list";
    }

    @GetMapping("/barters/{id}")
    public String detail(@PathVariable UUID id, Principal principal, Model model) {
        model.addAttribute("barter", barterService.detail(principal.getName(), id));
        return "barter/detail";
    }

    @PostMapping("/barters")
    public String create(@Valid @ModelAttribute("form") BarterForm form, BindingResult result,
                         Principal principal, RedirectAttributes ra) {
        String back = form.getTargetUserId() != null ? "/matching/" + form.getTargetUserId() : "/matching";
        if (result.hasErrors()) {
            ra.addFlashAttribute("error", firstError(result));
            return "redirect:" + back;
        }
        try {
            UUID id = barterService.create(principal.getName(), form);
            ra.addFlashAttribute("success", "Permintaan barter terkirim");
            return "redirect:" + PAGE + "/" + id;
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:" + back;
        }
    }

    @PostMapping("/barters/{id}/accept")
    public String accept(@PathVariable UUID id, Principal principal, RedirectAttributes ra) {
        return perform(ra, "Permintaan diterima. Silakan jadwalkan sesi belajar.", PAGE + "/" + id,
                () -> barterService.accept(principal.getName(), id));
    }

    @PostMapping("/barters/{id}/reject")
    public String reject(@PathVariable UUID id, Principal principal, RedirectAttributes ra) {
        return perform(ra, "Permintaan ditolak", PAGE + "/" + id,
                () -> barterService.reject(principal.getName(), id));
    }

    @PostMapping("/barters/{id}/cancel")
    public String cancel(@PathVariable UUID id, Principal principal, RedirectAttributes ra) {
        return perform(ra, "Permintaan dibatalkan", PAGE + "/" + id,
                () -> barterService.cancel(principal.getName(), id));
    }

    @PostMapping("/barters/{id}/complete")
    public String complete(@PathVariable UUID id, Principal principal, RedirectAttributes ra) {
        return perform(ra, "Barter ditandai selesai", PAGE + "/" + id,
                () -> barterService.complete(principal.getName(), id));
    }
}
