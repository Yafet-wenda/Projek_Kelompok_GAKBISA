package com.gakbisa.backend.dto;

import java.util.UUID;

/**
 * DTO untuk form tambah keahlian (OFFER / WANT).
 * Digunakan oleh Thymeleaf th:object pada template profile/skills.html
 */
public class UserSkillDto {

    private UUID skillId;
    private String skillName;
    private String type = "OFFER";
    private String level = "BEGINNER";
    private String notes;

    public UserSkillDto() {}

    public UserSkillDto(UUID skillId, String type, String level, String notes) {
        this.skillId = skillId;
        this.type = type;
        this.level = level;
        this.notes = notes;
    }

    public UUID getSkillId() {
        return skillId;
    }

    public void setSkillId(UUID skillId) {
        this.skillId = skillId;
    }

    public String getSkillName() {
        return skillName;
    }

    public void setSkillName(String skillName) {
        this.skillName = skillName;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}

