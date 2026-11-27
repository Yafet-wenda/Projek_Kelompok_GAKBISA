package com.gakbisa.backend.controller;

import com.gakbisa.backend.config.AppExceptions.ValidationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/learning-needs")
public class LearningNeedController {
  private final JdbcTemplate jdbc;
  public LearningNeedController(JdbcTemplate jdbc) { this.jdbc = jdbc; }
  @GetMapping public List<Map<String,Object>> list() {
    return jdbc.queryForList("SELECT n.id,n.user_id AS \"userId\",u.full_name AS \"fullName\",u.campus,n.topic,n.level,n.target_date AS \"targetDate\",n.details,n.created_at AS \"createdAt\" FROM learning_needs n JOIN users u ON u.id=n.user_id WHERE n.status='OPEN' ORDER BY n.created_at DESC");
  }
  @PostMapping public Map<String,Object> create(@RequestBody Body body) {
    String topic=body.topic()==null?"":body.topic().trim();
    if(topic.isBlank()||topic.length()>150) throw new ValidationException("Topik wajib diisi (maksimal 150 karakter)");
    String level=body.level()==null?"BEGINNER":body.level().toUpperCase();
    if(!List.of("BEGINNER","INTERMEDIATE","ADVANCED").contains(level)) throw new ValidationException("Level tidak valid");
    String details=body.details()==null?"":body.details().trim(); if(details.length()>2000) throw new ValidationException("Detail maksimal 2000 karakter");
    String target=body.targetDate()==null||body.targetDate().isBlank()?null:body.targetDate();
    UUID id=jdbc.queryForObject("INSERT INTO learning_needs(user_id,topic,level,target_date,details) VALUES (?,?,?,?::date,?) RETURNING id",UUID.class,CurrentUser.id(),topic,level,target,details);
    return Map.of("id",id,"status","OPEN");
  }
  @PostMapping("/{id}/close") public Map<String,Object> close(@PathVariable UUID id) {
    int n=jdbc.update("UPDATE learning_needs SET status='CLOSED' WHERE id=? AND user_id=? AND status='OPEN'",id,CurrentUser.id());
    if(n==0) throw new ValidationException("Kebutuhan tidak ditemukan atau bukan milikmu");
    return Map.of("id",id,"status","CLOSED");
  }
  public record Body(String topic,String level,String targetDate,String details) {}
}
