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

import java.util.Map;

/**
 * Data awal untuk pengembangan & demo: kategori/keahlian umum + akun demo.
 * Aktif hanya bila gakbisa.seed-data=true — matikan sebelum presentasi akhir.
 */
@Component
// Bean tidak dibuat sama sekali bila seed mati; dengan begitu start tanpa database tetap bersih.
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
        if (users.count() > 0) {
            log.info("Seed dilewati: database sudah berisi data pengguna.");
            return;
        }

        Map<String, SkillCategory> cats = Map.of(
            "Pemrograman", save("Pemrograman", "Bahasa pemrograman dan rekayasa perangkat lunak"),
            "Desain", save("Desain", "UI/UX, grafis, dan visual"),
            "Bahasa", save("Bahasa", "Bahasa asing dan komunikasi"),
            "Data", save("Data", "Analisis dan pengolahan data"));

        Skill java = skill("Java", cats.get("Pemrograman"));
        skill("Python", cats.get("Pemrograman"));
        skill("HTML/CSS", cats.get("Pemrograman"));
        skill("UI/UX", cats.get("Desain"));
        skill("Inggris", cats.get("Bahasa"));
        skill("Excel", cats.get("Data"));

        user("Admin GAKBISA", "admin@gakbisa.test", "admin123", UserRole.ADMIN);
        User yafet = user("Yafet Wenda", "yafet@gakbisa.test", "yafet123", UserRole.STUDENT);
        User faiz = user("Moh. Faiz", "faiz@gakbisa.test", "faiz123", UserRole.STUDENT);
        User nando = user("Haudajun Nando", "haudajun@gakbisa.test", "haudajun123", UserRole.STUDENT);

        offer(yafet, java, SkillLevel.INTERMEDIATE);
        want(yafet, skillByName("UI/UX"), SkillLevel.BEGINNER);
        offer(faiz, skillByName("UI/UX"), SkillLevel.ADVANCED);
        want(faiz, java, SkillLevel.BEGINNER);
        offer(nando, skillByName("Excel"), SkillLevel.INTERMEDIATE);
        want(nando, skillByName("Inggris"), SkillLevel.BEGINNER);

        log.info("Seed selesai: {} pengguna, {} keahlian. Login demo: yafet@gakbisa.test / yafet123",
            users.count(), skills.count());
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
        return skills.findByNameIgnoreCase(name).orElseThrow();
    }

    private User user(String name, String email, String pass, UserRole role) {
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
