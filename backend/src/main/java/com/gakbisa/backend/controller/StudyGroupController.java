package com.gakbisa.backend.controller;

import com.gakbisa.backend.config.AppExceptions.ForbiddenException;
import com.gakbisa.backend.config.AppExceptions.NotFoundException;
import com.gakbisa.backend.config.AppExceptions.ValidationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/groups")
public class StudyGroupController {
    private final JdbcTemplate jdbc;
    public StudyGroupController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping
    public List<Map<String, Object>> list() {
        UUID actor = CurrentUser.id();
        return jdbc.queryForList("""
            SELECT g.id, g.name, g.level, g.max_members AS "maxMembers", g.visibility, g.status,
                   s.name AS "skillName", g.owner_id AS "ownerId", u.full_name AS "ownerName",
                   EXISTS(SELECT 1 FROM user_skills wanted WHERE wanted.user_id=? AND wanted.type='WANT' AND wanted.skill_id=g.skill_id) AS "recommended",
                   (SELECT count(*) FROM group_members m WHERE m.group_id=g.id AND m.status='ACTIVE') AS "memberCount",
                   COALESCE((SELECT m.status FROM group_members m WHERE m.group_id=g.id AND m.user_id=?), 'NONE') AS "myStatus",
                   COALESCE((SELECT m.role FROM group_members m WHERE m.group_id=g.id AND m.user_id=? AND m.status='ACTIVE'), 'NONE') AS "myRole"
            FROM study_groups g JOIN skills s ON s.id=g.skill_id JOIN users u ON u.id=g.owner_id
            WHERE g.status='ACTIVE' AND (g.visibility='OPEN' OR g.owner_id=? OR EXISTS
              (SELECT 1 FROM group_members m WHERE m.group_id=g.id AND m.user_id=? AND m.status <> 'LEFT'))
            ORDER BY "recommended" DESC, g.created_at DESC
            """, actor, actor, actor, actor, actor);
    }

    @PostMapping
    @Transactional
    public Map<String, Object> create(@RequestBody CreateGroup body) {
        String name = body.name() == null ? "" : body.name().trim();
        if (name.isBlank() || name.length() > 120) throw new ValidationException("Nama kelompok wajib diisi (maks. 120 karakter)");
        if (body.skillId() == null) throw new ValidationException("Keahlian kelompok wajib dipilih dari katalog");
        if (body.maxMembers() < 2 || body.maxMembers() > 100) throw new ValidationException("Kapasitas kelompok harus 2 sampai 100 orang");
        String level = body.level() == null ? "INTERMEDIATE" : body.level().toUpperCase();
        if (!List.of("BEGINNER", "INTERMEDIATE", "ADVANCED").contains(level)) throw new ValidationException("Level kelompok tidak valid");
        String visibility = body.visibility() == null ? "OPEN" : body.visibility().toUpperCase();
        if (!List.of("OPEN", "INVITE").contains(visibility)) throw new ValidationException("Visibilitas kelompok tidak valid");
        if (jdbc.queryForObject("SELECT count(*) FROM skills WHERE id=?", Integer.class, body.skillId()) == 0) throw new NotFoundException("Keahlian tidak ditemukan");
        UUID actor = CurrentUser.id();
        UUID id = jdbc.queryForObject("INSERT INTO study_groups(owner_id,name,skill_id,level,max_members,visibility) VALUES (?,?,?,?,?,?) RETURNING id",
            UUID.class, actor, name, body.skillId(), level, body.maxMembers(), visibility);
        jdbc.update("INSERT INTO group_members(group_id,user_id,role,status) VALUES (?,?, 'OWNER','ACTIVE')", id, actor);
        return detail(id);
    }

    @PostMapping("/{id}/join")
    public Map<String, Object> join(@PathVariable UUID id) {
        Map<String, Object> group = find(id);
        if (!"ACTIVE".equals(group.get("status"))) throw new ValidationException("Kelompok sudah ditutup");
        UUID actor = CurrentUser.id();
        String prior = jdbc.query("SELECT status FROM group_members WHERE group_id=? AND user_id=?", rs -> rs.next() ? rs.getString(1) : null, id, actor);
        if ("ACTIVE".equals(prior)) throw new ValidationException("Kamu sudah menjadi anggota kelompok");
        int count = jdbc.queryForObject("SELECT count(*) FROM group_members WHERE group_id=? AND status='ACTIVE'", Integer.class, id);
        if (count >= ((Number) group.get("maxMembers")).intValue()) throw new ValidationException("Kelompok sudah penuh");
        String status = "OPEN".equals(group.get("visibility")) ? "ACTIVE" : "PENDING";
        if (prior == null) jdbc.update("INSERT INTO group_members(group_id,user_id,role,status) VALUES (?,?,'MEMBER',?)", id, actor, status);
        else jdbc.update("UPDATE group_members SET status=?, joined_at=now() WHERE group_id=? AND user_id=?", status, id, actor);
        return Map.of("status", status);
    }

    @PostMapping("/{id}/leave")
    public Map<String, Object> leave(@PathVariable UUID id) {
        String role = memberRole(id, CurrentUser.id());
        if ("OWNER".equals(role)) throw new ValidationException("Pemilik kelompok tidak dapat keluar. Tutup kelompok atau alihkan kepemilikan terlebih dahulu.");
        jdbc.update("UPDATE group_members SET status='LEFT' WHERE group_id=? AND user_id=?", id, CurrentUser.id());
        return Map.of("status", "LEFT");
    }

    @GetMapping("/{id}/members")
    public List<Map<String, Object>> members(@PathVariable UUID id) {
        memberRole(id, CurrentUser.id());
        return jdbc.queryForList("SELECT m.user_id AS \"userId\", u.full_name AS \"fullName\", m.role, m.status, m.joined_at AS \"joinedAt\" FROM group_members m JOIN users u ON u.id=m.user_id WHERE m.group_id=? AND m.status <> 'LEFT' ORDER BY m.joined_at", id);
    }

    @PostMapping("/{id}/members/{userId}/approve")
    @Transactional
    public Map<String, Object> approve(@PathVariable UUID id, @PathVariable UUID userId) {
        if (!"OWNER".equals(memberRole(id, CurrentUser.id()))) throw new ForbiddenException("Hanya pemilik kelompok yang dapat menyetujui anggota");
        Map<String, Object> group = find(id);
        int count = jdbc.queryForObject("SELECT count(*) FROM group_members WHERE group_id=? AND status='ACTIVE'", Integer.class, id);
        if (count >= ((Number) group.get("maxMembers")).intValue()) throw new ValidationException("Kelompok sudah penuh");
        int changed = jdbc.update("UPDATE group_members SET status='ACTIVE' WHERE group_id=? AND user_id=? AND status='PENDING'", id, userId);
        if (changed == 0) throw new ValidationException("Tidak ada permintaan bergabung yang menunggu");
        return Map.of("status", "ACTIVE");
    }

    @PostMapping("/{id}/transfer")
    @Transactional
    public Map<String, Object> transfer(@PathVariable UUID id, @RequestBody TransferBody body) {
        UUID actor = CurrentUser.id();
        if (!"OWNER".equals(memberRole(id, actor))) throw new ForbiddenException("Hanya pemilik kelompok yang dapat mengalihkan kepemilikan");
        if (body.newOwnerId() == null || actor.equals(body.newOwnerId())) throw new ValidationException("Pilih anggota lain sebagai pemilik baru");
        int target = jdbc.update("UPDATE group_members SET role='OWNER' WHERE group_id=? AND user_id=? AND status='ACTIVE' AND role='MEMBER'", id, body.newOwnerId());
        if (target == 0) throw new ValidationException("Pemilik baru harus anggota aktif kelompok");
        jdbc.update("UPDATE group_members SET role='MEMBER' WHERE group_id=? AND user_id=? AND status='ACTIVE'", id, actor);
        jdbc.update("UPDATE study_groups SET owner_id=? WHERE id=?", body.newOwnerId(), id);
        return Map.of("ownerId", body.newOwnerId());
    }

    @PostMapping("/{id}/close")
    @Transactional
    public Map<String, Object> close(@PathVariable UUID id) {
        if (!"OWNER".equals(memberRole(id, CurrentUser.id()))) throw new ForbiddenException("Hanya pemilik kelompok yang dapat menutup kelompok");
        int changed = jdbc.update("UPDATE study_groups SET status='CLOSED' WHERE id=? AND status='ACTIVE'", id);
        if (changed == 0) throw new ValidationException("Kelompok sudah ditutup");
        jdbc.update("UPDATE learning_sessions SET status='CANCELLED' WHERE group_id=? AND status='SCHEDULED'", id);
        return Map.of("status", "CLOSED");
    }

    private Map<String, Object> detail(UUID id) {
        return jdbc.queryForMap("SELECT g.id, g.name, g.level, g.max_members AS \"maxMembers\", g.visibility, g.status, s.name AS \"skillName\", g.owner_id AS \"ownerId\", u.full_name AS \"ownerName\", 1 AS \"memberCount\", 'ACTIVE' AS \"myStatus\" FROM study_groups g JOIN skills s ON s.id=g.skill_id JOIN users u ON u.id=g.owner_id WHERE g.id=?", id);
    }
    private Map<String, Object> find(UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, max_members AS \"maxMembers\", visibility, status FROM study_groups WHERE id=?", id);
        if (rows.isEmpty()) throw new NotFoundException("Kelompok tidak ditemukan");
        return rows.get(0);
    }
    private String memberRole(UUID groupId, UUID actor) {
        String role = jdbc.query("SELECT role FROM group_members WHERE group_id=? AND user_id=? AND status='ACTIVE'", rs -> rs.next() ? rs.getString(1) : null, groupId, actor);
        if (role == null) throw new ForbiddenException("Akses hanya untuk anggota kelompok aktif");
        return role;
    }
    public record CreateGroup(String name, UUID skillId, String level, int maxMembers, String visibility) {}
    public record TransferBody(UUID newOwnerId) {}
}
