package com.gakbisa.backend.service;

import com.gakbisa.backend.domain.Skill;
import com.gakbisa.backend.domain.SkillCategory;
import com.gakbisa.backend.domain.SkillLevel;
import com.gakbisa.backend.domain.SkillType;
import com.gakbisa.backend.domain.User;
import com.gakbisa.backend.repository.PortfolioRepository;
import com.gakbisa.backend.repository.ReviewRepository;
import com.gakbisa.backend.repository.SkillCategoryRepository;
import com.gakbisa.backend.repository.SkillRepository;
import com.gakbisa.backend.repository.UserRepository;
import com.gakbisa.backend.repository.UserSkillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Keahlian bisa ditulis langsung; nama baru masuk kategori "Lainnya" tanpa menunggu admin. */
class UserServiceSkillTest {

    private UserRepository users;
    private UserSkillRepository userSkills;
    private SkillRepository skills;
    private SkillCategoryRepository categories;
    private UserService service;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        users = mock(UserRepository.class);
        userSkills = mock(UserSkillRepository.class);
        skills = mock(SkillRepository.class);
        categories = mock(SkillCategoryRepository.class);
        service = new UserService(users, userSkills, skills, categories,
            mock(PortfolioRepository.class), mock(ReviewRepository.class));

        User u = new User();
        u.setId(userId);
        when(users.findById(userId)).thenReturn(Optional.of(u));
        when(userSkills.existsByUserIdAndSkill_IdAndType(eq(userId), any(), any())).thenReturn(false);
        when(userSkills.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(skills.save(any(Skill.class))).thenAnswer(inv -> {
            Skill s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });
        when(categories.save(any(SkillCategory.class))).thenAnswer(inv -> {
            SkillCategory c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });
    }

    @Test
    void namaBaruDibuatkanSkillDanKategoriLainnya() {
        when(skills.findByNameIgnoreCase("Blender")).thenReturn(Optional.empty());
        when(categories.findByNameIgnoreCase("Lainnya")).thenReturn(Optional.empty());

        var us = service.addSkill(userId, null, "Blender", SkillType.OFFER, SkillLevel.BEGINNER, null);

        assertEquals("Blender", us.getSkill().getName());
        assertEquals("Lainnya", us.getSkill().getCategory().getName());
        verify(categories).save(any(SkillCategory.class));
    }

    @Test
    void namaSudahAdaPakaiSkillKatalog() {
        Skill ada = new Skill();
        ada.setId(UUID.randomUUID());
        ada.setName("Figma");
        ada.setCategory(new SkillCategory());
        when(skills.findByNameIgnoreCase("figma")).thenReturn(Optional.of(ada));

        var us = service.addSkill(userId, null, "figma", SkillType.WANT, null, null);

        assertSame(ada, us.getSkill());
        assertEquals(SkillLevel.BEGINNER, us.getLevel(), "level kosong dianggap pemula");
        verify(skills, never()).save(any());
    }

    @Test
    void tanpaIdDanNamaDitolak() {
        assertThrows(com.gakbisa.backend.config.AppExceptions.ValidationException.class,
            () -> service.addSkill(userId, null, "   ", SkillType.OFFER, SkillLevel.ADVANCED, null));
    }
}
