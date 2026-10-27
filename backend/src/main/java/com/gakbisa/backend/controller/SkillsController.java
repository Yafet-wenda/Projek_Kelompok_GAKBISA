package com.gakbisa.backend.controller;

import com.gakbisa.backend.domain.SkillLevel;
import com.gakbisa.backend.domain.SkillType;
import com.gakbisa.backend.domain.UserSkill;
import com.gakbisa.backend.repository.SkillCategoryRepository;
import com.gakbisa.backend.repository.SkillRepository;
import com.gakbisa.backend.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class SkillsController {

    public record UserSkillDto(UUID id, UUID skillId, String skillName, String category,
                               SkillType type, SkillLevel level, String notes) {
        static UserSkillDto from(UserSkill us) {
            return new UserSkillDto(us.getId(), us.getSkill().getId(), us.getSkill().getName(),
                us.getSkill().getCategory().getName(), us.getType(), us.getLevel(), us.getNotes());
        }
    }

    public record AddSkillRequest(UUID skillId, SkillType type, SkillLevel level, String notes) {}

    private final UserService userService;
    private final SkillRepository skills;
    private final SkillCategoryRepository categories;

    public SkillsController(UserService userService, SkillRepository skills, SkillCategoryRepository categories) {
        this.userService = userService;
        this.skills = skills;
        this.categories = categories;
    }

    /** Katalog publik dengan pagination (kebutuhan nonfungsional: batas hasil). */
    @GetMapping("/skills")
    public Page<Map<String, Object>> catalog(@RequestParam(required = false) String q,
                                             @RequestParam(required = false) UUID categoryId,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        PageRequest pr = PageRequest.of(page, Math.min(size, 50));
        Page<com.gakbisa.backend.domain.Skill> result;
        if (q != null && !q.isBlank()) result = skills.search(q, pr);
        else if (categoryId != null) result = skills.findByCategory_Id(categoryId, pr);
        else result = skills.findAllBy(pr);
        return result.map(s -> Map.of(
            "id", s.getId(),
            "name", s.getName(),
            "category", s.getCategory().getName(),
            "description", s.getDescription() == null ? "" : s.getDescription()));
    }

    @GetMapping("/categories")
    public List<Map<String, Object>> categories() {
        return categories.findAll().stream()
            .map(c -> Map.<String, Object>of("id", c.getId(), "name", c.getName(),
                "description", c.getDescription() == null ? "" : c.getDescription()))
            .toList();
    }

    @GetMapping("/me/skills")
    public List<UserSkillDto> mySkills(@RequestParam(required = false) SkillType type) {
        return userService.listSkills(CurrentUser.id(), type).stream().map(UserSkillDto::from).toList();
    }

    @PostMapping("/me/skills")
    public UserSkillDto addSkill(@RequestBody AddSkillRequest req) {
        if (req.type() == null) throw new com.gakbisa.backend.config.AppExceptions.ValidationException("type wajib OFFER/WANT");
        return UserSkillDto.from(userService.addSkill(CurrentUser.id(), req.skillId(), req.type(), req.level(), req.notes()));
    }

    @DeleteMapping("/me/skills/{id}")
    public void removeSkill(@PathVariable UUID id) {
        userService.removeSkill(CurrentUser.id(), id);
    }
}
