package com.gakbisa.backend.config;

import com.gakbisa.backend.domain.AccountStatus;
import com.gakbisa.backend.domain.Skill;
import com.gakbisa.backend.domain.SkillCategory;
import com.gakbisa.backend.domain.SkillLevel;
import com.gakbisa.backend.domain.SkillType;
import com.gakbisa.backend.domain.User;
import com.gakbisa.backend.domain.UserRole;
import com.gakbisa.backend.domain.UserSkill;
import com.gakbisa.backend.repository.SkillCategoryRepository;
import com.gakbisa.backend.repository.SkillRepository;
import com.gakbisa.backend.repository.UserRepository;
import com.gakbisa.backend.repository.UserSkillRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * Data awal untuk pengembangan & demo: kategori/keahlian umum + akun demo.
 * Aktif bila gakbisa.seed-data=true (atau SEED_DATA=true di .env).
 */
@Component
@ConditionalOnProperty(prefix = "gakbisa", name = "seed-data", havingValue = "true")
public class SeedDataRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedDataRunner.class);

    private final UserRepository users;
    private final SkillCategoryRepository categories;
    private final SkillRepository skills;
    private final UserSkillRepository userSkills;
    private final PasswordEncoder encoder;

    public SeedDataRunner(UserRepository users, SkillCategoryRepository categories,
                          SkillRepository skills, UserSkillRepository userSkills, PasswordEncoder encoder) {
        this.users = users;
        this.categories = categories;
        this.skills = skills;
        this.userSkills = userSkills;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Map<String, SkillCategory> cats = new HashMap<>();

        if (categories.count() == 0) {
            cats.put("Pemrograman", save("Pemrograman", "Bahasa pemrograman dan rekayasa perangkat lunak"));
            cats.put("Desain", save("Desain", "UI/UX, grafis, dan visual"));
            cats.put("Bahasa", save("Bahasa", "Bahasa asing dan komunikasi"));
            cats.put("Data", save("Data", "Analisis dan pengolahan data"));

            skill("Java", cats.get("Pemrograman"));
            skill("Python", cats.get("Pemrograman"));
            skill("HTML/CSS", cats.get("Pemrograman"));
            skill("UI/UX", cats.get("Desain"));
            skill("Inggris", cats.get("Bahasa"));
            skill("Excel", cats.get("Data"));
            log.info("Kategori dan keahlian awal berhasil di-seed.");
        } else {
            for (SkillCategory sc : categories.findAll()) {
                cats.put(sc.getName(), sc);
            }
        }

        if (!users.existsByEmail("yafet@gakbisa.test")) {
            user("Admin GAKBISA", "admin@gakbisa.test", "admin123", UserRole.ADMIN);
            User yafet = user("Yafet Wenda", "yafet@gakbisa.test", "yafet123", UserRole.STUDENT);
            User faiz = user("Moh. Faiz", "faiz@gakbisa.test", "faiz123", UserRole.STUDENT);
            User nando = user("Haudajun Nando", "haudajun@gakbisa.test", "haudajun123", UserRole.STUDENT);

            Skill javaSkill = skillByName("Java");
            Skill uiuxSkill = skillByName("UI/UX");
            Skill excelSkill = skillByName("Excel");
            Skill inggrisSkill = skillByName("Inggris");

            if (javaSkill != null && yafet != null) offer(yafet, javaSkill, SkillLevel.INTERMEDIATE);
            if (uiuxSkill != null && yafet != null) want(yafet, uiuxSkill, SkillLevel.BEGINNER);
            if (uiuxSkill != null && faiz != null) offer(faiz, uiuxSkill, SkillLevel.ADVANCED);
            if (javaSkill != null && faiz != null) want(faiz, javaSkill, SkillLevel.BEGINNER);
            if (excelSkill != null && nando != null) offer(nando, excelSkill, SkillLevel.INTERMEDIATE);
            if (inggrisSkill != null && nando != null) want(nando, inggrisSkill, SkillLevel.BEGINNER);

            log.info("Akun demo kelompok berhasil di-seed (Yafet, Faiz, Nando, Admin).");
        }
    }

    private SkillCategory save(String name, String desc) {
        SkillCategory c = new SkillCategory();
        c.setName(name);
        c.setDescription(desc);
        return categories.save(c);
    }

    private Skill skill(String name, SkillCategory category) {
        Skill s = new Skill();
        s.setName(name);
        s.setCategory(category);
        return skills.save(s);
    }

    private Skill skillByName(String name) {
        return skills.findByNameIgnoreCase(name).orElse(null);
    }

    private User user(String name, String email, String pass, UserRole role) {
        if (users.existsByEmail(email)) return users.findByEmail(email).orElse(null);
        User u = new User();
        u.setFullName(name);
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(pass));
        u.setRole(role);
        u.setStatus(AccountStatus.ACTIVE);
        return users.save(u);
    }

    private void offer(User u, Skill s, SkillLevel level) { upsert(u, s, SkillType.OFFER, level); }
    private void want(User u, Skill s, SkillLevel level) { upsert(u, s, SkillType.WANT, level); }

    private void upsert(User u, Skill s, SkillType type, SkillLevel level) {
        if (userSkills.existsByUserIdAndSkill_IdAndType(u.getId(), s.getId(), type)) return;
        UserSkill us = new UserSkill();
        us.setUser(u);
        us.setSkill(s);
        us.setType(type);
        us.setLevel(level);
        userSkills.save(us);
    }
}
