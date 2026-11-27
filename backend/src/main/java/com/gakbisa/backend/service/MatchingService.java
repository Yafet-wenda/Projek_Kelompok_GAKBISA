package com.gakbisa.backend.service;

import com.gakbisa.backend.config.AppExceptions.NotFoundException;
import com.gakbisa.backend.domain.SkillLevel;
import com.gakbisa.backend.domain.SkillType;
import com.gakbisa.backend.domain.User;
import com.gakbisa.backend.domain.UserSkill;
import com.gakbisa.backend.repository.PortfolioRepository;
import com.gakbisa.backend.repository.ReviewRepository;
import com.gakbisa.backend.repository.UserRepository;
import com.gakbisa.backend.repository.UserSkillRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Algoritma skill-matching rule-based sesuai proposal §4.3.
 *
 * Bobot (maks 100):
 *  - 50 poin: keahlian WANT kita tersedia di OFFER kandidat (kecocokan kebutuhan utama)
 *  - 30 poin: keahlian WANT kandidat tersedia di OFFER kita (barter dua arah)
 *  - 10 poin: level keahlian kandidat memenuhi level yang kita butuhkan
 *  -  5 poin: kelengkapan profil/portofolio kandidat
 *  -  5 poin: reputasi kandidat dari ulasan (pengguna baru diperlakukan netral)
 */
@Service
public class MatchingService {

    static final int NEED_MATCH = 50;
    static final int MUTUAL_MATCH = 30;
    static final int LEVEL_MATCH = 10;
    static final int PROFILE_BIO = 2;
    static final int PROFILE_COMPLETE = 5;
    static final int REPUTATION = 5;

    private final UserRepository users;
    private final UserSkillRepository userSkills;
    private final PortfolioRepository portfolios;
    private final ReviewRepository reviews;

    public MatchingService(UserRepository users, UserSkillRepository userSkills,
                           PortfolioRepository portfolios, ReviewRepository reviews) {
        this.users = users;
        this.userSkills = userSkills;
        this.portfolios = portfolios;
        this.reviews = reviews;
    }

    public record Candidate(UUID userId, String fullName, String campus, String studyProgram,
                            int score, List<String> reasons) {}

    public record SearchFilters(String keyword, UUID categoryId, SkillLevel level,
                                String campus, String studyProgram) {
        boolean active() {
            return notBlank(keyword) || categoryId != null || level != null
                || notBlank(campus) || notBlank(studyProgram);
        }
    }

    @Transactional(readOnly = true)
    public List<Candidate> recommend(UUID userId, int limit) {
        return calculate(userId, null, limit);
    }

    @Transactional(readOnly = true)
    public Page<Candidate> search(UUID userId, SearchFilters filters, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 50));
        List<Candidate> matches = calculate(userId, filters, Integer.MAX_VALUE);
        int from = Math.min(safePage * safeSize, matches.size());
        int to = Math.min(from + safeSize, matches.size());
        return new PageImpl<>(matches.subList(from, to), PageRequest.of(safePage, safeSize), matches.size());
    }

    private List<Candidate> calculate(UUID userId, SearchFilters filters, int limit) {
        users.findById(userId).orElseThrow(() -> new NotFoundException("Pengguna tidak ditemukan"));

        List<UserSkill> mySkills = userSkills.findByUserId(userId);
        Map<String, UserSkill> myOffer = indexBySkillName(filter(mySkills, SkillType.OFFER));
        List<UserSkill> myWant = filter(mySkills, SkillType.WANT);

        int capped = Math.max(1, Math.min(limit, 10_000));
        List<Candidate> result = new ArrayList<>();

        for (User other : users.findAll()) {
            if (other.getId().equals(userId)
                || other.getStatus() == com.gakbisa.backend.domain.AccountStatus.SUSPENDED) continue;

            List<UserSkill> theirSkills = userSkills.findByUserId(other.getId());
            Map<String, UserSkill> theirOffer = indexBySkillName(filter(theirSkills, SkillType.OFFER));
            List<UserSkill> theirWantList = filter(theirSkills, SkillType.WANT);

            if (filters != null && filters.active() && !matches(filters, other, filter(theirSkills, SkillType.OFFER))) continue;

            int score = 0;
            List<String> reasons = new ArrayList<>();
            boolean skillRelated = false;

            // 1) Kecocokan kebutuhan utama (50)
            boolean needMatched = false;
            UserSkill bestNeed = null;
            for (UserSkill w : myWant) {
                UserSkill o = theirOffer.get(name(w));
                if (o != null) {
                    if (!needMatched) score += NEED_MATCH;
                    needMatched = true;
                    skillRelated = true;
                    bestNeed = bestNeed == null ? o : bestNeed;
                    reasons.add("Kamu mencari " + w.getSkill().getName() + ", dan " + other.getFullName()
                        + " menawarkan keahlian itu (level " + o.getLevel() + ")");
                }
            }

            // 2) Barter dua arah (30)
            for (UserSkill tw : theirWantList) {
                if (myOffer.containsKey(name(tw))) {
                    score += MUTUAL_MATCH;
                    skillRelated = true;
                    reasons.add("Pertukaran dua arah: " + other.getFullName() + " mencari "
                        + tw.getSkill().getName() + ", dan kamu mengajarkannya");
                    break;
                }
            }

            // 3) Kesesuaian level (10) — level kandidat memenuhi kebutuhan teratas kita
            if (bestNeed != null && !myWant.isEmpty()) {
                SkillLevel required = levelOfFirstMatch(myWant, theirOffer);
                if (required != null && bestNeed.getLevel().ordinal() >= required.ordinal()) {
                    score += LEVEL_MATCH;
                    reasons.add("Level " + bestNeed.getLevel() + " memenuhi kebutuhan belajarmu");
                }
            }

            // Kandidat tanpa satu pun irisan keahlian tidak direkomendasikan
            // (pencarian manual per kategori tetap tersedia). Berhenti di sini agar query
            // portofolio dan ulasan di bawah tidak dijalankan sia-sia untuk kandidat itu.
            if (!skillRelated && (filters == null || !filters.active())) continue;
            if (!skillRelated) reasons.add("Hasil pencarian manual; belum ada kecocokan skill otomatis");

            // 4) Kelengkapan profil/portofolio (5)
            long theirPortfolios = portfolios.countByUserId(other.getId());
            int profilePts = 0;
            if (other.getBio() != null && !other.getBio().isBlank()) profilePts += PROFILE_BIO;
            if (theirPortfolios > 0) profilePts += PROFILE_COMPLETE - PROFILE_BIO;
            if (profilePts > 0) {
                score += profilePts;
                reasons.add("Profil " + (theirPortfolios > 0 ? "lengkap dengan portofolio" : "berisi deskripsi"));
            }

            // 5) Reputasi (5), pengguna baru netral (3)
            Double avg = reviews.averageRatingFor(other.getId());
            if (avg == null) {
                score += 3;
                reasons.add("Pengguna baru — reputasi belum tersedia");
            } else {
                score += (int) Math.round(REPUTATION * (avg / 5.0));
                reasons.add("Reputasi " + String.format("%.1f", avg) + "/5");
            }

            result.add(new Candidate(other.getId(), other.getFullName(), other.getCampus(), other.getStudyProgram(),
                Math.min(score, 100), reasons));
        }

        result.sort((left, right) -> Integer.compare(right.score(), left.score()));
        return result.size() > capped ? result.subList(0, capped) : result;
    }

    private static boolean matches(SearchFilters filters, User user, List<UserSkill> offers) {
        if (notBlank(filters.campus()) && !containsIgnoreCase(user.getCampus(), filters.campus())) return false;
        if (notBlank(filters.studyProgram()) && !containsIgnoreCase(user.getStudyProgram(), filters.studyProgram())) return false;

        String keyword = filters.keyword();
        boolean keywordMatches = !notBlank(keyword)
            || containsIgnoreCase(user.getFullName(), keyword)
            || containsIgnoreCase(user.getCampus(), keyword)
            || containsIgnoreCase(user.getStudyProgram(), keyword)
            || offers.stream().anyMatch(skill -> containsIgnoreCase(skill.getSkill().getName(), keyword)
                || containsIgnoreCase(skill.getSkill().getCategory().getName(), keyword));
        if (!keywordMatches) return false;

        if (filters.categoryId() == null && filters.level() == null) return true;
        return offers.stream().anyMatch(skill -> (filters.categoryId() == null
                || filters.categoryId().equals(skill.getSkill().getCategory().getId()))
            && (filters.level() == null || filters.level() == skill.getLevel()));
    }

    private static boolean containsIgnoreCase(String value, String query) {
        return value != null && query != null
            && value.toLowerCase().contains(query.trim().toLowerCase());
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static SkillLevel levelOfFirstMatch(List<UserSkill> wants, Map<String, UserSkill> offers) {
        for (UserSkill w : wants) {
            UserSkill o = offers.get(name(w));
            if (o != null) return w.getLevel();
        }
        return null;
    }

    private static List<UserSkill> filter(List<UserSkill> list, SkillType type) {
        return list.stream().filter(us -> us.getType() == type).toList();
    }

    private static Map<String, UserSkill> indexBySkillName(List<UserSkill> list) {
        return list.stream().collect(Collectors.toMap(MatchingService::name, Function.identity(), (a, b) -> a));
    }

    private static String name(UserSkill us) {
        return us.getSkill().getName().toLowerCase();
    }
}
