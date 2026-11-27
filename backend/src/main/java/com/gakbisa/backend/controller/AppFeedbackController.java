package com.gakbisa.backend.controller;

import com.gakbisa.backend.config.AppExceptions.ValidationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.Map;

@RestController
@RequestMapping("/api/feedback")
public class AppFeedbackController {
  private final JdbcTemplate jdbc;
  public AppFeedbackController(JdbcTemplate jdbc) { this.jdbc=jdbc; }
  @PostMapping public Map<String,Object> submit(@RequestBody Body body) {
    String message=body.message()==null?"":body.message().trim();
    if(message.length()<5||message.length()>2000) throw new ValidationException("Masukan harus berisi 5–2000 karakter");
    return jdbc.queryForMap("INSERT INTO app_feedback(user_id,message) VALUES (?,?) RETURNING id,created_at AS \"createdAt\"",CurrentUser.id(),message);
  }
  @GetMapping("/admin") @PreAuthorize("hasRole('ADMIN')") public java.util.List<Map<String,Object>> list() { return jdbc.queryForList("SELECT f.id,u.full_name AS \"fullName\",f.message,f.created_at AS \"createdAt\" FROM app_feedback f JOIN users u ON u.id=f.user_id ORDER BY f.created_at DESC LIMIT 200"); }
  public record Body(String message) {}
}
