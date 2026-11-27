package com.gakbisa.backend.controller;
import com.gakbisa.backend.config.AppExceptions.ValidationException;
import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.transaction.annotation.Transactional; import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/api/admin/chat-reports") @PreAuthorize("hasRole('ADMIN')")
public class ChatReportAdminController {
 private final JdbcTemplate jdbc; public ChatReportAdminController(JdbcTemplate jdbc){this.jdbc=jdbc;}
 @GetMapping public List<Map<String,Object>> list(){return jdbc.queryForList("SELECT r.id,r.reason,r.status,r.created_at AS \"createdAt\",u.full_name AS \"reporterName\",s.full_name AS \"senderName\",m.id AS \"messageId\" FROM chat_message_reports r JOIN users u ON u.id=r.reporter_id JOIN chat_messages m ON m.id=r.message_id JOIN users s ON s.id=m.sender_id WHERE r.status='OPEN' ORDER BY r.created_at");}
 @PostMapping("/{id}/review") @Transactional public Map<String,Object> review(@PathVariable UUID id,@RequestBody Body body){String status=body.dismiss()?"DISMISSED":"RESOLVED";int n=jdbc.update("UPDATE chat_message_reports SET status=? WHERE id=? AND status='OPEN'",status,id);if(n==0)throw new ValidationException("Laporan tidak ditemukan atau telah ditinjau");return Map.of("id",id,"status",status);}
 public record Body(boolean dismiss){}
}
