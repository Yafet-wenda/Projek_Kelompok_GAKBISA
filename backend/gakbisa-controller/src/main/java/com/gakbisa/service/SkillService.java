package com.gakbisa.service;

import com.gakbisa.dto.Forms.UserSkillForm;
import com.gakbisa.dto.Views.CategoryView;
import com.gakbisa.dto.Views.SkillView;
import com.gakbisa.dto.Views.UserSkillView;

import java.util.List;
import java.util.UUID;

public interface SkillService {
    List<CategoryView> listCategories();
    List<SkillView> listSkillsByCategory(UUID categoryId);
    List<UserSkillView> listUserSkills(String email);
    void addUserSkill(String email, UserSkillForm form);
    void updateUserSkill(String email, UUID userSkillId, UserSkillForm form);
    /** Gagal (BusinessException) bila keahlian masih dipakai permintaan barter aktif. */
    void deleteUserSkill(String email, UUID userSkillId);
}
