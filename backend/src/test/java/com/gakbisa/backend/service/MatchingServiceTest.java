package com.gakbisa.backend.service;

import com.gakbisa.backend.domain.*;
import com.gakbisa.backend.repository.PortfolioRepository;
import com.gakbisa.backend.repository.ReviewRepository;
import com.gakbisa.backend.repository.UserRepository;
import com.gakbisa.backend.repository.UserSkillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Skenario uji §8.2 no.5: pencocokan satu arah dan dua arah dengan skor & alasan konsisten. */
class MatchingServiceTest {

    private UserRepository users;
    private UserSkillRepository userSkills;
    private PortfolioRepository portfolios;
    private ReviewRepository reviews;
    private MatchingService service;

    private User me;
    private User twoWay;
    private User oneWay;
    private User stranger;

    @BeforeEach
    @SuppressWarnings("unused")
    void setup() {
        users = mock(UserRepository.class);
        userSkills = mock(UserSkillRepository.class);
        portfolios = mock(PortfolioRepository.class);
        reviews = mock(ReviewRepository.class);
        service = new MatchingService(users, userSkills, portfolios, reviews);

        me = user("Me");
        twoWay = user("TwoWay");
        oneWay = user("OneWay");
        stranger = user("Stranger");
        when(users.findAll()).thenReturn(List.of(me, twoWay, oneWay, stranger));
        when(users.findById(any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return java.util.Arrays.stream(new User[]{me, twoWay, oneWay, stranger})
                .filter(u -> u.getId().equals(id)).findFirst();
        });

        Skill java = skill("Java");
        Skill ux = skill("UI/UX");

        // Me: OFFER Java (advanced), WANT UI/UX (beginner)
        when(userSkills.findByUserId(me.getId())).thenReturn(List.of(
            us(java, SkillType.OFFER, SkillLevel.ADVANCED),
            us(ux, SkillType.WANT, SkillLevel.BEGINNER)));
        // TwoWay: OFFER UI/UX (intermediate — memenuhi kebutuhan), WANT Java
        when(userSkills.findByUserId(twoWay.getId())).thenReturn(List.of(
            us(ux, SkillType.OFFER, SkillLevel.INTERMEDIATE),
            us(java, SkillType.WANT, SkillLevel.BEGINNER)));
        // OneWay: OFFER UI/UX saja, tidak mencari apa pun
        when(userSkills.findByUserId(oneWay.getId())).thenReturn(List.of(
            us(ux, SkillType.OFFER, SkillLevel.BEGINNER)));
        // Stranger: tidak ada keahlian

        when(portfolios.countByUserId(any())).thenReturn(0L);
        when(reviews.averageRatingFor(any())).thenReturn(null); // semua pengguna baru -> netral 3
    }

    @Test
    void duaArahSkorLebihTinggiDariSatuArah() {
        List<MatchingService.Candidate> result = service.recommend(me.getId(), 10);

        var t = result.stream().filter(c -> c.userId().equals(twoWay.getId())).findFirst().orElseThrow();
        var o = result.stream().filter(c -> c.userId().equals(oneWay.getId())).findFirst().orElseThrow();

        // twoWay: 50 (kebutuhan) + 30 (dua arah) + 10 (level) + 3 (reputasi netral) = 93
        // oneWay: 50 (kebutuhan) + 10 (level) + 3 (reputasi netral) = 63, tanpa 30 karena tidak mencari Java
        assertEquals(93, t.score(), "alasan: " + t.reasons());
        assertEquals(63, o.score(), "alasan: " + o.reasons());
        assertTrue(t.score() > o.score());
        assertTrue(t.reasons().stream().anyMatch(r -> r.contains("dua arah")));
        assertTrue(t.reasons().stream().anyMatch(r -> r.contains("UI/UX")));
    }

    @Test
    void penggunaTanpaKecocokanTidakDirekomendasikanAtauSkorRendah() {
        List<MatchingService.Candidate> result = service.recommend(me.getId(), 10);
        assertTrue(result.stream().noneMatch(c -> c.userId().equals(stranger.getId())),
            "stranger tanpa keahlian tidak muncul");
    }

    @Test
    void skorDibatasiMaksimalSeratus() {
        twoWay.setBio("Desainer UI/UX");
        when(portfolios.countByUserId(twoWay.getId())).thenReturn(3L);
        when(reviews.averageRatingFor(twoWay.getId())).thenReturn(5.0);
        List<MatchingService.Candidate> result = service.recommend(me.getId(), 10);
        var t = result.stream().filter(c -> c.userId().equals(twoWay.getId())).findFirst().orElseThrow();
        // 50 + 30 + 10 + 5 (bio & portofolio) + 5 (rating 5/5) = 100
        assertEquals(100, t.score());
    }

    // ---- helper pembuat objek ----

    private static User user(String name) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setFullName(name);
        u.setEmail(name.toLowerCase() + "@gakbisa.test");
        u.setPasswordHash("x");
        u.setRole(UserRole.STUDENT);
        u.setStatus(AccountStatus.ACTIVE);
        return u;
    }

    private static Skill skill(String name) {
        Skill s = new Skill();
        s.setId(UUID.randomUUID());
        s.setName(name);
        SkillCategory c = new SkillCategory();
        c.setId(UUID.randomUUID());
        c.setName("Umum");
        s.setCategory(c);
        return s;
    }

    private static UserSkill us(Skill skill, SkillType type, SkillLevel level) {
        UserSkill us = new UserSkill();
        us.setId(UUID.randomUUID());
        us.setSkill(skill);
        us.setType(type);
        us.setLevel(level);
        return us;
    }
}
