package com.gakbisa.backend.controller;

import com.gakbisa.backend.config.AppExceptions.ValidationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.net.URI;
import java.net.http.*;

@RestController
public class CampusVerificationController {
    private final JdbcTemplate jdbc;
    private final List<String> allowedDomains;
    @Value("${gakbisa.mail.api-key:}") private String mailApiKey;
    @Value("${gakbisa.mail.from:GAKBISA <onboarding@resend.dev>}") private String mailFrom;
    @Value("${gakbisa.mail.api-url:https://api.resend.com/emails}") private String mailApiUrl;
    public CampusVerificationController(JdbcTemplate jdbc,
        @Value("${gakbisa.verification.allowed-domains:ac.id}") String domains) {
        this.jdbc = jdbc;
        this.allowedDomains = java.util.Arrays.stream(domains.split(",")).map(s -> s.trim()).filter(s -> !s.isBlank()).toList();
    }

    @PostMapping("/api/me/campus-verification")
    @Transactional
    public Map<String, Object> request(@RequestBody VerificationRequest body) {
        String email = body.campusEmail() == null ? "" : body.campusEmail().trim().toLowerCase();
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw new ValidationException("Alamat email kampus tidak valid");
        String domain = email.substring(email.lastIndexOf('@') + 1);
        if (allowedDomains.stream().noneMatch(d -> domain.equals(d) || domain.endsWith("." + d))) {
            throw new ValidationException("Gunakan alamat kampus dengan domain yang diizinkan: " + String.join(", ", allowedDomains));
        }
        if (mailApiKey == null || mailApiKey.isBlank()) throw new ValidationException("Verifikasi email kampus belum aktif. Admin perlu mengonfigurasi layanan email agar kode konfirmasi dapat dikirim.");
        String code=String.format("%06d",new SecureRandom().nextInt(1_000_000));
        String hash=sha256(code);
        jdbc.update("INSERT INTO campus_verification_requests(user_id,campus_email,status,requested_at,reviewed_at,reviewed_by,code_hash,code_expires_at,email_confirmed_at) VALUES (?,?, 'PENDING',now(),NULL,NULL,?,now()+interval '15 minutes',NULL) ON CONFLICT(user_id,campus_email) DO UPDATE SET status='PENDING',requested_at=now(),reviewed_at=NULL,reviewed_by=NULL,code_hash=EXCLUDED.code_hash,code_expires_at=EXCLUDED.code_expires_at,email_confirmed_at=NULL",
            CurrentUser.id(), email, hash);
        sendCode(email,code);
        return status();
    }

    @PostMapping("/api/me/campus-verification/confirm") @Transactional
    public Map<String,Object> confirm(@RequestBody ConfirmBody body) {
        String code=body.code()==null?"":body.code().trim();
        if(!code.matches("\\d{6}")) throw new ValidationException("Masukkan kode 6 digit");
        UUID user=CurrentUser.id();
        List<UUID> ids=jdbc.query("SELECT id FROM campus_verification_requests WHERE user_id=? AND status='PENDING' AND code_expires_at>now() AND code_hash=? ORDER BY requested_at DESC LIMIT 1",(rs,n)->(UUID)rs.getObject(1),user,sha256(code));
        if(ids.isEmpty()) throw new ValidationException("Kode salah atau sudah kedaluwarsa. Minta kode baru.");
        jdbc.update("UPDATE campus_verification_requests SET status='APPROVED',email_confirmed_at=now(),reviewed_at=now() WHERE id=?",ids.getFirst());
        jdbc.update("UPDATE users SET is_verified=TRUE WHERE id=?",user);
        return Map.of("verified",true,"status","APPROVED");
    }

    private String sha256(String text) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8))); } catch(Exception e) { throw new IllegalStateException(e); } }
    private void sendCode(String email,String code) {
        try (HttpClient client = HttpClient.newHttpClient()) {
            String json="{\"from\":\""+mailFrom.replace("\\","\\\\").replace("\"","\\\"")+"\",\"to\":[\""+email+"\"],\"subject\":\"Kode verifikasi kampus GAKBISA\",\"text\":\"Kode verifikasi kamu: "+code+". Kode berlaku 15 menit.\"}";
            HttpRequest req=HttpRequest.newBuilder(URI.create(mailApiUrl)).header("Authorization","Bearer "+mailApiKey).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json)).build();
            HttpResponse<Void> res=client.send(req,HttpResponse.BodyHandlers.discarding());
            if(res.statusCode()<200||res.statusCode()>=300) throw new IllegalStateException("mail provider status "+res.statusCode());
        } catch(Exception e) { throw new ValidationException("Kode gagal dikirim. Periksa konfigurasi layanan email lalu coba lagi."); }
    }

    @GetMapping("/api/me/campus-verification")
    public Map<String, Object> status() {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT campus_email AS \"campusEmail\",status,requested_at AS \"requestedAt\",reviewed_at AS \"reviewedAt\" FROM campus_verification_requests WHERE user_id=? ORDER BY requested_at DESC LIMIT 1", CurrentUser.id());
        if (rows.isEmpty()) return Map.of("verified", jdbc.queryForObject("SELECT is_verified FROM users WHERE id=?", Boolean.class, CurrentUser.id()), "status", "NONE");
        Map<String, Object> result = rows.getFirst();
        result.put("verified", jdbc.queryForObject("SELECT is_verified FROM users WHERE id=?", Boolean.class, CurrentUser.id()));
        return result;
    }

    @GetMapping("/api/admin/campus-verifications")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String, Object>> pending() {
        return jdbc.queryForList("SELECT v.id,v.campus_email AS \"campusEmail\",v.requested_at AS \"requestedAt\",u.id AS \"userId\",u.full_name AS \"fullName\",u.email AS \"accountEmail\",u.campus FROM campus_verification_requests v JOIN users u ON u.id=v.user_id WHERE v.status='PENDING' ORDER BY v.requested_at");
    }

    @PostMapping("/api/admin/campus-verifications/{id}/review")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public Map<String, Object> review(@PathVariable UUID id, @RequestBody ReviewBody body) {
        UUID userId = jdbc.query("SELECT user_id FROM campus_verification_requests WHERE id=? AND status='PENDING'", rs -> rs.next() ? (UUID) rs.getObject(1) : null, id);
        if (userId == null) throw new ValidationException("Permintaan tidak ada atau sudah ditinjau");
        if (body.approved()) {
            Boolean confirmed=jdbc.queryForObject("SELECT email_confirmed_at IS NOT NULL FROM campus_verification_requests WHERE id=?",Boolean.class,id);
            if(!Boolean.TRUE.equals(confirmed)) throw new ValidationException("Email kampus harus dikonfirmasi dengan kode sebelum disetujui");
        }
        String decision = body.approved() ? "APPROVED" : "REJECTED";
        jdbc.update("UPDATE campus_verification_requests SET status=?,reviewed_at=now(),reviewed_by=? WHERE id=?", decision, CurrentUser.id(), id);
        if (body.approved()) jdbc.update("UPDATE users SET is_verified=TRUE WHERE id=?", userId);
        return Map.of("id", id, "status", decision);
    }

    public record VerificationRequest(String campusEmail) {}
    public record ReviewBody(boolean approved) {}
    public record ConfirmBody(String code) {}
}
