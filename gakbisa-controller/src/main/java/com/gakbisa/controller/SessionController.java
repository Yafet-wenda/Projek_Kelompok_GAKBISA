package com.gakbisa.controller;

import com.gakbisa.dto.Forms.ReviewForm;
import com.gakbisa.dto.Forms.SessionForm;
import com.gakbisa.exception.BusinessException;
import com.gakbisa.service.BarterService;
import com.gakbisa.service.SessionService;
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

/** Penjadwalan sesi belajar + halaman video meeting (Jitsi IFrame API). */
@Controller
public class SessionController extends BaseController {

    private final SessionService sessionService;
    private final BarterService barterService;

    public SessionController(SessionService sessionService, BarterService barterService) {
        this.sessionService = sessionService;
        this.barterService = barterService;
    }

    @GetMapping("/sessions")
    public String list(Principal principal, Model model) {
        model.addAttribute("sessions", sessionService.list(principal.getName()));
        return "session/list";
    }

    @GetMapping("/barters/{barterId}/sessions/new")
    public String newForm(@PathVariable UUID barterId, Principal principal, Model model) {
        model.addAttribute("barter", barterService.detail(principal.getName(), barterId));
        model.addAttribute("form", new SessionForm());
        return "session/form";
    }

    @PostMapping("/barters/{barterId}/sessions")
    public String create(@PathVariable UUID barterId,
                         @Valid @ModelAttribute("form") SessionForm form, BindingResult result,
                         Principal principal, Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            model.addAttribute("barter", barterService.detail(principal.getName(), barterId));
            return "session/form";
        }
        try {
            UUID sessionId = sessionService.schedule(principal.getName(), barterId, form);
            ra.addFlashAttribute("success", "Sesi belajar berhasil dijadwalkan");
            return "redirect:/sessions/" + sessionId;
        } catch (BusinessException e) {
            // Contoh: barter belum ACCEPTED -> sesi ditolak
            model.addAttribute("barter", barterService.detail(principal.getName(), barterId));
            model.addAttribute("error", e.getMessage());
            return "session/form";
        }
    }

    @GetMapping("/sessions/{id}")
    public String detail(@PathVariable UUID id, Principal principal, Model model) {
        model.addAttribute("session", sessionService.detail(principal.getName(), id));
        if (!model.containsAttribute("reviewForm")) {
            model.addAttribute("reviewForm", new ReviewForm());
        }
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new SessionForm());
        }
        return "session/detail";
    }

    /** Halaman meeting. Peserta diverifikasi di service sebelum info ruang diberikan. */
    @GetMapping("/sessions/{id}/meeting")
    public String meeting(@PathVariable UUID id, Principal principal, Model model, RedirectAttributes ra) {
        try {
            model.addAttribute("meeting", sessionService.joinMeeting(principal.getName(), id));
            model.addAttribute("sessionId", id);
            return "session/meeting";
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/sessions/" + id;
        }
    }

    @PostMapping("/sessions/{id}/reschedule")
    public String reschedule(@PathVariable UUID id, @Valid @ModelAttribute("form") SessionForm form,
                             BindingResult result, Principal principal, RedirectAttributes ra) {
        if (result.hasErrors()) {
            ra.addFlashAttribute("error", firstError(result));
            return "redirect:/sessions/" + id;
        }
        return perform(ra, "Jadwal sesi diperbarui", "/sessions/" + id,
                () -> sessionService.reschedule(principal.getName(), id, form));
    }

    @PostMapping("/sessions/{id}/cancel")
    public String cancel(@PathVariable UUID id, Principal principal, RedirectAttributes ra) {
        return perform(ra, "Sesi dibatalkan", "/sessions/" + id,
                () -> sessionService.cancel(principal.getName(), id));
    }

    @PostMapping("/sessions/{id}/complete")
    public String complete(@PathVariable UUID id, Principal principal, RedirectAttributes ra) {
        return perform(ra, "Sesi ditandai selesai. Silakan beri ulasan.", "/sessions/" + id,
                () -> sessionService.complete(principal.getName(), id));
    }
}
