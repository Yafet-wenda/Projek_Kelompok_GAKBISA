package com.gakbisa.backend.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class SessionAutomationScheduler {
    private final JdbcTemplate jdbc;
    public SessionAutomationScheduler(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void sendRemindersAndProcessNoShows() {
        Instant now = Instant.now();
        remind(now, 1440);
        remind(now, 60);
        processNoShows(now);
        remindUnscheduledBarters(now);
        remindReviews(now);
    }

    private void remindUnscheduledBarters(Instant now) {
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT b.id,b.requester_id,b.target_user_id FROM barter_requests b WHERE b.status='ACCEPTED' AND b.created_at<? AND NOT EXISTS(SELECT 1 FROM learning_sessions s WHERE s.barter_request_id=b.id AND s.status NOT IN ('CANCELLED'))",Timestamp.from(now.minusSeconds(86400)));
        for(Map<String,Object> r:rows) for(String column:List.of("requester_id","target_user_id")) {
            UUID uid=(UUID)r.get(column); UUID bid=(UUID)r.get("id");
            if(jdbc.update("INSERT INTO workflow_reminders(user_id,reference_id,reminder_type) VALUES (?,?,'UNSCHEDULED_BARTER') ON CONFLICT DO NOTHING",uid,bid)>0)
                jdbc.update("INSERT INTO notifications(user_id,title,body,type) VALUES (?,'Barter belum dijadwalkan','Barter yang diterima sudah lebih dari 24 jam. Buka halaman barter untuk mengatur sesi belajar.','SESSION')",uid);
        }
    }
    private void remindReviews(Instant now) {
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT s.id,b.requester_id,b.target_user_id FROM learning_sessions s JOIN barter_requests b ON b.id=s.barter_request_id WHERE s.status='COMPLETED' AND s.completed_at<?",Timestamp.from(now.minusSeconds(86400)));
        for(Map<String,Object> r:rows) for(String column:List.of("requester_id","target_user_id")) {
            UUID uid=(UUID)r.get(column); UUID sid=(UUID)r.get("id");
            if(jdbc.queryForObject("SELECT count(*) FROM reviews WHERE learning_session_id=? AND reviewer_id=?",Integer.class,sid,uid)>0) continue;
            if(jdbc.update("INSERT INTO workflow_reminders(user_id,reference_id,reminder_type) VALUES (?,?,'SESSION_REVIEW') ON CONFLICT DO NOTHING",uid,sid)>0)
                jdbc.update("INSERT INTO notifications(user_id,title,body,type) VALUES (?,'Ulasan sesi menunggu','Bagikan pengalamanmu untuk sesi belajar yang telah selesai.','REVIEW')",uid);
        }
    }

    private void remind(Instant now, int minutes) {
        Instant from = now.plusSeconds((minutes - 5L) * 60);
        Instant until = now.plusSeconds(minutes * 60L);
        List<Map<String, Object>> due = jdbc.queryForList("""
            SELECT s.id AS session_id, p.user_id, s.scheduled_at
            FROM learning_sessions s JOIN barter_requests b ON b.id=s.barter_request_id
            CROSS JOIN LATERAL (VALUES (b.requester_id),(b.target_user_id)) p(user_id)
            WHERE s.status='SCHEDULED' AND s.schedule_confirmed=TRUE AND s.scheduled_at>=? AND s.scheduled_at<?
            UNION ALL
            SELECT s.id, gm.user_id, s.scheduled_at FROM learning_sessions s
            JOIN group_members gm ON gm.group_id=s.group_id AND gm.status='ACTIVE'
            WHERE s.group_id IS NOT NULL AND s.status='SCHEDULED' AND s.schedule_confirmed=TRUE AND s.scheduled_at>=? AND s.scheduled_at<?
            """, Timestamp.from(from), Timestamp.from(until), Timestamp.from(from), Timestamp.from(until));
        for (Map<String, Object> row : due) {
            UUID sessionId = (UUID) row.get("session_id");
            UUID userId = (UUID) row.get("user_id");
            int inserted = jdbc.update("INSERT INTO session_reminders(learning_session_id,user_id,remind_minutes) VALUES (?,?,?) ON CONFLICT DO NOTHING", sessionId, userId, minutes);
            if (inserted > 0) {
                String when = String.valueOf(row.get("scheduled_at"));
                jdbc.update("INSERT INTO notifications(user_id,title,body,type) VALUES (?,?,?,'SESSION')",
                    userId, "Pengingat sesi belajar", "Sesi dimulai sekitar " + minutes + " menit lagi (" + when + ").");
            }
        }
    }

    private void processNoShows(Instant now) {
        List<UUID> finished = jdbc.query("""
            SELECT id FROM learning_sessions WHERE barter_request_id IS NOT NULL AND schedule_confirmed=TRUE AND status IN ('SCHEDULED','ONGOING','COMPLETED')
              AND no_show_processed=FALSE AND scheduled_at + make_interval(mins => duration_minutes) + interval '30 minutes' < ?
            """, (rs, n) -> (UUID) rs.getObject(1), Timestamp.from(now));
        for (UUID sessionId : finished) {
            List<UUID> participants = jdbc.query("SELECT requester_id FROM learning_sessions s JOIN barter_requests b ON b.id=s.barter_request_id WHERE s.id=? UNION SELECT target_user_id FROM learning_sessions s JOIN barter_requests b ON b.id=s.barter_request_id WHERE s.id=?",
                (rs, n) -> (UUID) rs.getObject(1), sessionId, sessionId);
            List<UUID> checkedIn = jdbc.query("SELECT user_id FROM session_attendance WHERE learning_session_id=? AND attended=TRUE", (rs, n) -> (UUID) rs.getObject(1), sessionId);
            if (participants.size() == 2) {
                for (UUID absent : participants.stream().filter(id -> !checkedIn.contains(id)).toList()) {
                    jdbc.update("UPDATE users SET strike_count=strike_count+1 WHERE id=?", absent);
                    String detail = checkedIn.isEmpty()
                        ? "Tidak ada peserta yang mengonfirmasi kehadiran. Strike ditambahkan untuk kedua pihak; ajukan sengketa jika pencatatan keliru."
                        : "Partner mengonfirmasi kehadiran, sementara kamu tidak melakukan check-in. Strike ditambahkan; ajukan sengketa jika pencatatan keliru.";
                    jdbc.update("INSERT INTO notifications(user_id,title,body,type) VALUES (?,'Ketidakhadiran dicatat',?,'MODERATION')", absent, detail);
                }
            }
            jdbc.update("UPDATE learning_sessions SET no_show_processed=TRUE WHERE id=?", sessionId);
        }
    }
}
