package com.gakbisa.backend.controller;

import com.gakbisa.backend.config.AppExceptions.ForbiddenException;
import com.gakbisa.backend.config.AppExceptions.NotFoundException;
import com.gakbisa.backend.config.AppExceptions.ValidationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/groups/{groupId}/chat")
public class StudyGroupChatController {
    private final JdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();
    public StudyGroupChatController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping
    public List<Map<String, Object>> list(@PathVariable UUID groupId) {
        UUID room = roomForMember(groupId, CurrentUser.id());
        return jdbc.queryForList("""
            SELECT m.id, u.id AS "senderId", u.full_name AS "senderName", m.ciphertext, m.created_at AS "createdAt"
            FROM chat_messages m JOIN users u ON u.id=m.sender_id WHERE m.room_id=? AND m.deleted_at IS NULL
            ORDER BY m.created_at
            """, room).stream().map(row -> {
                Object encrypted = row.get("ciphertext");
                String text = "";
                if (encrypted != null) try { text = new String(Base64.getDecoder().decode(encrypted.toString()), StandardCharsets.UTF_8); } catch (Exception ignored) { text = encrypted.toString(); }
                row.put("content", text);
                row.put("isMe", CurrentUser.id().toString().equals(row.get("senderId").toString()));
                row.remove("ciphertext");
                return row;
            }).toList();
    }

    @PostMapping
    public Map<String, Object> send(@PathVariable UUID groupId, @RequestBody MessageBody body) {
        UUID sender = CurrentUser.id();
        UUID room = roomForMember(groupId, sender);
        String text = body.message() == null ? "" : body.message().trim();
        if (text.isBlank()) throw new ValidationException("Pesan tidak boleh kosong");
        if (text.length() > 2000) throw new ValidationException("Pesan maksimal 2000 karakter");
        UUID messageId = UUID.randomUUID();
        byte[] iv = new byte[12]; random.nextBytes(iv);
        jdbc.update("INSERT INTO chat_messages(id,room_id,sender_id,key_version,message_type,ciphertext,iv) VALUES (?,?,?,1,'TEXT',?,?)",
            messageId, room, sender, Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)), Base64.getEncoder().encodeToString(iv));
        jdbc.update("INSERT INTO notifications(user_id,title,body,type) SELECT gm.user_id,?,?, 'CHAT' FROM group_members gm WHERE gm.group_id=? AND gm.status='ACTIVE' AND gm.user_id<>?", "Pesan baru di kelompok", text.length()>60?text.substring(0,57)+"…":text, groupId, sender);
        return Map.of("id", messageId, "content", text, "senderId", sender, "isMe", true, "createdAt", Instant.now());
    }

    @DeleteMapping("/{messageId}") public Map<String,Object> delete(@PathVariable UUID groupId,@PathVariable UUID messageId) {
        UUID room=roomForMember(groupId,CurrentUser.id());
        int n=jdbc.update("UPDATE chat_messages SET deleted_at=now(),ciphertext=NULL,iv=NULL WHERE id=? AND room_id=? AND sender_id=? AND deleted_at IS NULL",messageId,room,CurrentUser.id());
        if(n==0) throw new ValidationException("Pesan tidak ditemukan atau bukan milikmu");
        return Map.of("deleted",true);
    }
    @PostMapping("/{messageId}/report") public Map<String,Object> report(@PathVariable UUID groupId,@PathVariable UUID messageId,@RequestBody ReportBody body) {
        UUID room=roomForMember(groupId,CurrentUser.id()); String reason=body.reason()==null?"":body.reason().trim();
        if(reason.length()<5||reason.length()>500) throw new ValidationException("Alasan laporan harus 5–500 karakter");
        Integer valid=jdbc.queryForObject("SELECT count(*) FROM chat_messages WHERE id=? AND room_id=? AND sender_id<>? AND deleted_at IS NULL",Integer.class,messageId,room,CurrentUser.id());
        if(valid==null||valid==0) throw new ValidationException("Pesan tidak ditemukan atau tidak dapat dilaporkan");
        jdbc.update("INSERT INTO chat_message_reports(message_id,reporter_id,reason) VALUES (?,?,?) ON CONFLICT(message_id,reporter_id) DO NOTHING",messageId,CurrentUser.id(),reason);
        return Map.of("reported",true);
    }

    private UUID roomForMember(UUID groupId, UUID userId) {
        String groupStatus = jdbc.query("SELECT status FROM study_groups WHERE id=?", rs -> rs.next() ? rs.getString(1) : null, groupId);
        if (!"ACTIVE".equals(groupStatus)) throw new NotFoundException("Kelompok tidak ditemukan atau sudah ditutup");
        Integer member = jdbc.queryForObject("SELECT count(*) FROM group_members WHERE group_id=? AND user_id=? AND status='ACTIVE'", Integer.class, groupId, userId);
        if (member == null || member == 0) throw new ForbiddenException("Chat hanya dapat dibuka anggota kelompok aktif");
        List<UUID> existing = jdbc.query("SELECT id FROM chat_rooms WHERE study_group_id=?", rs -> rs.next() ? List.of((UUID) rs.getObject(1)) : List.of(), groupId);
        UUID room;
        if (existing.isEmpty()) {
            String name = jdbc.query("SELECT name FROM study_groups WHERE id=? AND status='ACTIVE'", rs -> rs.next() ? rs.getString(1) : null, groupId);
            if (name == null) throw new NotFoundException("Kelompok tidak ditemukan atau sudah ditutup");
            UUID owner = jdbc.queryForObject("SELECT owner_id FROM study_groups WHERE id=?", UUID.class, groupId);
            room = jdbc.queryForObject("INSERT INTO chat_rooms(type,name,created_by,study_group_id) VALUES ('GROUP',?,?,?) RETURNING id", UUID.class, name, owner, groupId);
        } else room = existing.getFirst();
        jdbc.update("INSERT INTO chat_members(room_id,user_id,role) VALUES (?,?,'MEMBER') ON CONFLICT(room_id,user_id) DO UPDATE SET left_at=NULL", room, userId);
        return room;
    }
    public record MessageBody(String message) {}
    public record ReportBody(String reason) {}
}
