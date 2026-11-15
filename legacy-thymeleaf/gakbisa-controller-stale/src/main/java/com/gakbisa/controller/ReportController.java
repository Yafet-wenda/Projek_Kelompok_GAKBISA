package com.gakbisa.controller;

import com.gakbisa.dto.Forms.ReportForm;
import com.gakbisa.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

/** Laporan portofolio/pengguna oleh mahasiswa; ditinjau admin. */
@Controller
public class ReportController extends BaseController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping("/reports")
    public String create(@Valid @ModelAttribute ReportForm form, BindingResult result,
                         @RequestParam(required = false) String redirectTo,
                         Principal principal, RedirectAttributes ra) {
        String back = safePath(redirectTo, "/dashboard");
        if (result.hasErrors()) {
            ra.addFlashAttribute("error", firstError(result));
            return "redirect:" + back;
        }
        return perform(ra, "Laporan terkirim dan akan ditinjau admin", back,
                () -> reportService.create(principal.getName(), form));
    }
}
