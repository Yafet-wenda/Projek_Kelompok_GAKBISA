package com.gakbisa.backend.service;

import com.gakbisa.backend.config.AppExceptions.ForbiddenException;
import com.gakbisa.backend.config.AppExceptions.NotFoundException;
import com.gakbisa.backend.config.AppExceptions.ValidationException;
import com.gakbisa.backend.domain.Skill;
import com.gakbisa.backend.domain.SkillCategory;
import com.gakbisa.backend.domain.SkillLevel;
import com.gakbisa.backend.domain.SkillType;
import com.gakbisa.backend.domain.User;
import com.gakbisa.backend.domain.UserSkill;
import com.gakbisa.backend.repository.PortfolioRepository;
import com.gakbisa.backend.repository.ReviewRepository;
import com.gakbisa.backend.repository.SkillCategoryRepository;
import com.gakbisa.backend.repository.SkillRepository;
import com.gakbisa.backend.repository.UserRepository;
import com.gakbisa.backend.repository.UserSkillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository users;
    private final UserSkillRepository userSkills;
    private final SkillRepository skills;
    private final SkillCategoryRepository categories;
    private final PortfolioRepository portfolios;
    private final ReviewRepository reviews;

    public UserService(UserRepository users, UserSkillRepository userSkills, SkillRepository skills,
                       SkillCategoryRepository categories, PortfolioRepository portfolios, ReviewRepository reviews) {
        this.users = users;
        this.userSkills = userSkills;
        this.skills = skills;
        this.categories = categories;
        this.portfolios = portfolios;
        this.reviews = reviews;
    }

    @Transactional(readOnly = true)
    public User require(UUID id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("Pengguna tidak ditemukan"));
    }

    /** Perbarui profil; hanya pemilik yang boleh mengubah (kecuali admin). */
    @Transactional
    public User updateProfile(UUID actorId, UUID targetId, String fullName, String bio, String campus) {
        User actor = require(actorId);
        if (!actor.getId().equals(targetId) && actor.getRole() != com.gakbisa.backend.domain.UserRole.ADMIN) {
            throw new ForbiddenException("Hanya pemilik atau admin yang dapat mengubah profil");
        }
        User u = require(targetId);
        if (fullName != null && !fullName.isBlank()) u.setFullName(fullName.trim());
        if (bio != null) u.setBio(bio);
        if (campus != null) u.setCampus(campus.trim());
        return users.save(u);
    }

    @Transactional(readOnly = true)
    public List<UserSkill> listSkills(UUID userId, SkillType type) {
        return type == null
            ? userSkills.findByUserId(userId)
            : userSkills.findByUserIdAndType(userId, type);
    }

    /** Tambah keahlian OFFER/WANT beserta level dan catatan. */
    @Transactional
    public UserSkill addSkill(UUID userId, UUID skillId, String skillName,
                              SkillType type, SkillLevel level, String notes) {
        User u = require(userId);
        Skill skill = resolveSkill(skillId, skillName);
        SkillLevel lv = level == null ? SkillLevel.BEGINNER : level;
        if (userSkills.existsByUserIdAndSkill_IdAndType(userId, skill.getId(), type)) {
            throw new ValidationException("Keahlian ini sudah ada dalam daftar Anda");
        }
        UserSkill us = new UserSkill();
        us.setUser(u);
        us.setSkill(skill);
        us.setType(type);
        us.setLevel(lv);
        us.setNotes(notes);
        return userSkills.save(us);
    }

    /**
     * Keahlian bisa dipilih dari katalog (skillId) atau ditulis langsung (skillName).
     * Nama baru yang belum ada di katalog dibuat di kategori "Lainnya" supaya pengguna
     * tidak perlu menunggu admin; admin tetap yang menentukan kategori akhirnya.
     */
    private Skill resolveSkill(UUID skillId, String skillName) {
        if (skillId != null) {
            return skills.findById(skillId).orElseThrow(() -> new NotFoundException("Keahlian tidak ditemukan"));
        }
        if (skillName == null || skillName.isBlank()) {
            throw new ValidationException("Pilih keahlian dari katalog atau tulis namanya");
        }
        String nama = skillName.trim();
        return skills.findByNameIgnoreCase(nama).orElseGet(() -> skills.save(buatSkillBaru(nama)));
    }

    private Skill buatSkillBaru(String nama) {
        SkillCategory lain = categories.findByNameIgnoreCase("Lainnya").orElseGet(() -> {
            SkillCategory c = new SkillCategory();
            c.setName("Lainnya");
            c.setDescription("Keahlian yang ditulis pengguna dan belum dikategorikan admin");
            return categories.save(c);
        });
        Skill s = new Skill();
        s.setName(nama);
        s.setCategory(lain);
        return s;
    }

    @Transactional
    public void removeSkill(UUID userId, UUID userSkillId) {
        UserSkill us = userSkills.findById(userSkillId)
            .orElseThrow(() -> new NotFoundException("Keahlian pengguna tidak ditemukan"));
        if (!us.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Keahlian bukan milik Anda");
        }
        userSkills.delete(us);
    }

    /** Ringkasan reputasi untuk profil: rating rata-rata, jumlah ulasan, jumlah portofolio. */
    @Transactional(readOnly = true)
    public ReputationDto reputation(UUID userId) {
        require(userId);
        Double avg = reviews.averageRatingFor(userId);
        return new ReputationDto(
            avg == null ? null : Math.round(avg * 10) / 10.0,
            reviews.countByReviewerIdOrRevieweeId(userId, userId),
            portfolios.countByUserId(userId)
        );
    }

    public record ReputationDto(Double averageRating, long reviewCount, long portfolioCount) {}

    /** Kartu profil untuk katalog publik: identitas + reputasi + apa yang dia ajarkan. */
    public record PublicProfile(UUID id, String fullName, String campus, Double averageRating,
                                long reviewCount, long portfolioCount, List<String> offers) {}

    @Transactional(readOnly = true)
    public PublicProfile publicProfile(UUID userId) {
        User u = require(userId);
        if (u.getStatus() != com.gakbisa.backend.domain.AccountStatus.ACTIVE) {
            throw new NotFoundException("Profil tidak tersedia");
        }
        ReputationDto r = reputation(userId);
        List<String> diajarkan = listSkills(userId, SkillType.OFFER).stream()
            .map(us -> us.getSkill().getName() + " · " + us.getLevel().name())
            .toList();
        return new PublicProfile(u.getId(), u.getFullName(),
            u.getCampus() == null ? "" : u.getCampus(),
            r.averageRating(), r.reviewCount(), r.portfolioCount(), diajarkan);
    }
}
