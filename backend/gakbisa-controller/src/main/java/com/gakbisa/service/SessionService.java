package com.gakbisa.service;

import com.gakbisa.dto.Forms.SessionForm;
import com.gakbisa.dto.Views.MeetingInfo;
import com.gakbisa.dto.Views.SessionView;

import java.util.List;
import java.util.UUID;

public interface SessionService {
    /** Tolak bila barter belum ACCEPTED. Generate meeting_room acak (UUID). Kembalikan id sesi. */
    UUID schedule(String email, UUID barterId, SessionForm form);
    List<SessionView> list(String email);
    List<SessionView> upcoming(String email, int limit);
    SessionView detail(String email, UUID sessionId);
    void reschedule(String email, UUID sessionId, SessionForm form);
    void cancel(String email, UUID sessionId);
    void complete(String email, UUID sessionId);
    /** Verifikasi peserta sesi; BusinessException jika bukan peserta / sesi batal / selesai. */
    MeetingInfo joinMeeting(String email, UUID sessionId);
}
