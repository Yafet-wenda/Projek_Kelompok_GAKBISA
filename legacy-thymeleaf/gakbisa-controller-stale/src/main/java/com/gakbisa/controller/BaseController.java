package com.gakbisa.controller;

import com.gakbisa.exception.BusinessException;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Helper bersama agar controller tetap ringkas. */
public abstract class BaseController {

    protected static final int PAGE_SIZE = 10;

    /** Jalankan aksi; pesan sukses/gagal dikirim sebagai flash attribute. */
    protected String perform(RedirectAttributes ra, String successMessage, String redirectTo, Runnable action) {
        try {
            action.run();
            ra.addFlashAttribute("success", successMessage);
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:" + redirectTo;
    }

    protected String firstError(BindingResult result) {
        return result.getAllErrors().isEmpty() ? "Input tidak valid"
                : result.getAllErrors().get(0).getDefaultMessage();
    }

    /** Cegah open redirect: hanya path internal yang diterima. */
    protected String safePath(String candidate, String fallback) {
        if (candidate != null && candidate.startsWith("/") && !candidate.startsWith("//")
                && !candidate.contains("\\") && !candidate.contains("\r") && !candidate.contains("\n")) {
            return candidate;
        }
        return fallback;
    }

    protected int safePage(int page) {
        return Math.max(page, 0);
    }
}
