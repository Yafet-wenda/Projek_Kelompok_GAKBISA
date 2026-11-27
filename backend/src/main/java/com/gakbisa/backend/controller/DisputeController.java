package com.gakbisa.backend.controller;

import com.gakbisa.backend.config.AppExceptions.NotFoundException;
import com.gakbisa.backend.config.AppExceptions.ValidationException;
import com.gakbisa.backend.service.BarterService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/disputes")
public class DisputeController {
    private final JdbcTemplate jdbc;
    private final BarterService barters;
    public DisputeController(JdbcTemplate jdbc, BarterService barters) { this.jdbc = jdbc; this.barters = barters; }

    @PostMapping
    public Map<String, Object> create(@RequestBody CreateDispute body) {
        var barter = barters.viewFor(CurrentUser.id(), body.barterRequestId());
        String reason = body.reason() == null ? "" : body.reason().trim();
        if (reason.isBlank() || reason.length() > 2000) throw new ValidationException("Alasan sengketa wajib diisi (maks. 2000 karakter)");
        UUID partner = barter.getRequester().getId().equals(CurrentUser.id()) ? barter.getTargetUser().getId() : barter.getRequester().getId();
        UUID id = jdbc.queryForObject("INSERT INTO disputes(barter_request_id,reporter_id,partner_id,reason) VALUES (?,?,?,?) RETURNING id", UUID.class, barter.getId(), CurrentUser.id(), partner, reason);
        return Map.of("id", id, "status", "OPEN");
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String, Object>> list() {
        return jdbc.queryForList("""
          SELECT d.id, d.barter_request_id AS "barterRequestId", d.reporter_id AS "reporterId", d.partner_id AS "partnerId", d.reason, d.status, d.resolution, d.created_at AS "createdAt",
                 r.full_name AS "reporterName", p.full_name AS "partnerName"
          FROM disputes d JOIN users r ON r.id=d.reporter_id JOIN users p ON p.id=d.partner_id
          WHERE d.status='OPEN' ORDER BY d.created_at
          """);
    }

    @PostMapping("/admin/{id}/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> resolve(@PathVariable UUID id, @RequestBody ResolveDispute body) {
        String resolution = body.resolution() == null ? "" : body.resolution().trim();
        if (resolution.isBlank() || resolution.length() > 2000) throw new ValidationException("Catatan keputusan wajib diisi (maks. 2000 karakter)");
        List<Map<String, Object>> parties = jdbc.queryForList("SELECT reporter_id,partner_id FROM disputes WHERE id=? AND status='OPEN'", id);
        if (parties.isEmpty()) throw new NotFoundException("Sengketa tidak ditemukan atau sudah ditutup");
        jdbc.update("UPDATE disputes SET status='RESOLVED', resolution=?, resolved_at=now() WHERE id=?", resolution, id);
        UUID reporter = (UUID) parties.getFirst().get("reporter_id");
        UUID partner = (UUID) parties.getFirst().get("partner_id");
        if (body.addStrikeToPartner()) jdbc.update("UPDATE users SET strike_count=strike_count+1 WHERE id=?", partner);
        if (body.removeStrikeFromReporter()) jdbc.update("UPDATE users SET strike_count=GREATEST(0,strike_count-1) WHERE id=?", reporter);
        return Map.of("id", id, "status", "RESOLVED");
    }
    public record CreateDispute(UUID barterRequestId, String reason) {}
    public record ResolveDispute(String resolution, boolean addStrikeToPartner, boolean removeStrikeFromReporter) {}
}
