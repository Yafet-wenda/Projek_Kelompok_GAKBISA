package com.gakbisa.backend.controller;
import com.gakbisa.backend.config.AppExceptions.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets; import java.time.Instant; import java.util.*;
@RestController @RequestMapping("/api/barter/{barterId}/chat")
public class BarterChatController {
 private final JdbcTemplate jdbc; public BarterChatController(JdbcTemplate jdbc){this.jdbc=jdbc;}
 @GetMapping public List<Map<String,Object>> list(@PathVariable UUID barterId){UUID room=room(barterId);return jdbc.queryForList("SELECT m.id,m.sender_id AS \"senderId\",u.full_name AS \"senderName\",m.ciphertext,m.created_at AS \"createdAt\" FROM chat_messages m JOIN users u ON u.id=m.sender_id WHERE m.room_id=? AND m.deleted_at IS NULL ORDER BY m.created_at",room).stream().map(r->{try{r.put("content",new String(Base64.getDecoder().decode(r.get("ciphertext").toString()),StandardCharsets.UTF_8));}catch(Exception e){r.put("content","");}r.put("isMe",CurrentUser.id().toString().equals(r.get("senderId").toString()));r.remove("ciphertext");return r;}).toList();}
 @PostMapping public Map<String,Object> send(@PathVariable UUID barterId,@RequestBody Body body){UUID room=room(barterId);String text=body.message()==null?"":body.message().trim();if(text.isBlank()||text.length()>2000)throw new ValidationException("Pesan wajib diisi dan maksimal 2000 karakter");UUID id=UUID.randomUUID();byte[] iv=new byte[12];new java.security.SecureRandom().nextBytes(iv);jdbc.update("INSERT INTO chat_messages(id,room_id,sender_id,key_version,message_type,ciphertext,iv) VALUES (?,?,?,1,'TEXT',?,?)",id,room,CurrentUser.id(),Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)),Base64.getEncoder().encodeToString(iv));jdbc.update("INSERT INTO notifications(user_id,title,body,type) SELECT CASE WHEN requester_id=? THEN target_user_id ELSE requester_id END,'Pesan baru di barter',?,'CHAT' FROM barter_requests WHERE id=?",CurrentUser.id(),text.length()>60?text.substring(0,57)+"…":text,barterId);return Map.of("id",id,"content",text,"senderId",CurrentUser.id(),"isMe",true,"createdAt",Instant.now());}
 @DeleteMapping("/{messageId}") public Map<String,Object> delete(@PathVariable UUID barterId,@PathVariable UUID messageId){UUID room=room(barterId);int n=jdbc.update("UPDATE chat_messages SET deleted_at=now(),ciphertext=NULL,iv=NULL WHERE id=? AND room_id=? AND sender_id=? AND deleted_at IS NULL",messageId,room,CurrentUser.id());if(n==0)throw new ValidationException("Pesan tidak ditemukan atau bukan milikmu");return Map.of("deleted",true);}
 @PostMapping("/{messageId}/report") public Map<String,Object> report(@PathVariable UUID barterId,@PathVariable UUID messageId,@RequestBody ReportBody body){UUID room=room(barterId);String reason=body.reason()==null?"":body.reason().trim();if(reason.length()<5||reason.length()>500)throw new ValidationException("Alasan laporan harus 5–500 karakter");Integer n=jdbc.queryForObject("SELECT count(*) FROM chat_messages WHERE id=? AND room_id=? AND sender_id<>? AND deleted_at IS NULL",Integer.class,messageId,room,CurrentUser.id());if(n==null||n==0)throw new ValidationException("Pesan tidak ditemukan atau tidak dapat dilaporkan");jdbc.update("INSERT INTO chat_message_reports(message_id,reporter_id,reason) VALUES (?,?,?) ON CONFLICT(message_id,reporter_id) DO NOTHING",messageId,CurrentUser.id(),reason);return Map.of("reported",true);}
 private UUID room(UUID barter) {
  List<UUID> parties = jdbc.query("SELECT requester_id,target_user_id FROM barter_requests WHERE id=? AND status IN ('ACCEPTED','COMPLETED')",
      (rs,n) -> List.of((UUID)rs.getObject(1),(UUID)rs.getObject(2)), barter).stream().findFirst()
      .orElseThrow(() -> new NotFoundException("Barter belum diterima atau tidak ditemukan"));
  if (!parties.contains(CurrentUser.id())) throw new ForbiddenException("Chat ini hanya untuk peserta barter");
  String key = "barter:" + barter;
  List<UUID> ids = jdbc.query("SELECT id FROM chat_rooms WHERE direct_key=?", (rs,n) -> (UUID)rs.getObject(1), key);
  UUID id;
  if (ids.isEmpty()) {
   List<UUID> inserted = jdbc.query("INSERT INTO chat_rooms(type,direct_key,created_by,current_key_version) VALUES ('PRIVATE',?,?,1) ON CONFLICT(direct_key) DO NOTHING RETURNING id",
       (rs,n) -> (UUID)rs.getObject(1), key, parties.getFirst());
   id = inserted.isEmpty() ? jdbc.queryForObject("SELECT id FROM chat_rooms WHERE direct_key=?", UUID.class, key) : inserted.getFirst();
  } else id = ids.getFirst();
  for (UUID user : parties) jdbc.update("INSERT INTO chat_members(room_id,user_id,role) VALUES (?,?,'MEMBER') ON CONFLICT(room_id,user_id) DO UPDATE SET left_at=NULL", id, user);
  return id;
 }
 public record Body(String message){}
 public record ReportBody(String reason){}
}
