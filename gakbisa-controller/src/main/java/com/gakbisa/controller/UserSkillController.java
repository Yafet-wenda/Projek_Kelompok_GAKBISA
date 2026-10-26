package com.gakbisa.controller;

import com.gakbisa.dto.Forms.UserSkillForm;
import com.gakbisa.dto.Views.SkillView;
import com.gakbisa.service.SkillService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

/** Kelola keahlian OFFER (dikuasai) dan WANT (ingin dipelajari). */
@Controller
public class UserSkillController extends BaseController {

    private static final String PAGE = "/skills";

    private final SkillService skillService;

    public UserSkillController(SkillService skillService) {
        this.skillService = skillService;
    }

    @GetMapping("/skills")
    public String manage(Principal principal, Model model) {
        model.addAttribute("userSkills", skillService.listUserSkills(principal.getName()));
        model.addAttribute("categories", skillService.listCategories());
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new UserSkillForm());
        }
        return "skills/manage";
    }

    /** Untuk dropdown bertingkat (kategori -> keahlian) lewat fetch() di JavaScript. */
    @GetMapping("/skills/options")
    @ResponseBody
    public List<SkillView> options(@RequestParam UUID categoryId) {
        return skillService.listSkillsByCategory(categoryId);
    }

    @PostMapping("/skills")
    public String add(@Valid @ModelAttribute("form") UserSkillForm form, BindingResult result,
                      Principal principal, RedirectAttributes ra) {
        if (result.hasErrors()) {
            ra.addFlashAttribute("error", firstError(result));
            return "redirect:" + PAGE;
        }
        return perform(ra, "Keahlian berhasil ditambahkan", PAGE,
                () -> skillService.addUserSkill(principal.getName(), form));
    }

    @PostMapping("/skills/{id}/update")
    public String update(@PathVariable UUID id, @Valid @ModelAttribute("form") UserSkillForm form,
                         BindingResult result, Principal principal, RedirectAttributes ra) {
        if (result.hasErrors()) {
            ra.addFlashAttribute("error", firstError(result));
            return "redirect:" + PAGE;
        }
        return perform(ra, "Keahlian berhasil diubah", PAGE,
                () -> skillService.updateUserSkill(principal.getName(), id, form));
    }

    @PostMapping("/skills/{id}/delete")
    public String delete(@PathVariable UUID id, Principal principal, RedirectAttributes ra) {
        return perform(ra, "Keahlian berhasil dihapus", PAGE,
                () -> skillService.deleteUserSkill(principal.getName(), id));
    }
}
