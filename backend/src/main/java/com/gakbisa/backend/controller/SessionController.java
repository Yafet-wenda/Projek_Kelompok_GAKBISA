package com.gakbisa.backend.controller;

import com.gakbisa.backend.domain.SessionStatus;
import com.gakbisa.backend.dto.SessionCreateRequest;
import com.gakbisa.backend.dto.SessionDto;
import com.gakbisa.backend.service.SessionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessions;

    public SessionController(SessionService sessions) {
        this.sessions = sessions;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SessionDto schedule(@Valid @RequestBody SessionCreateRequest req) {
        return SessionDto.from(sessions.schedule(CurrentUser.id(), req));
    }

    @GetMapping
    public List<SessionDto> mine() {
        return sessions.forUser(CurrentUser.id()).stream().map(SessionDto::from).toList();
    }

    /** meetingUrl hanya dikembalikan untuk peserta sesi (validasi di service). */
    @GetMapping("/{id}")
    public SessionDto view(@PathVariable UUID id) {
        return SessionDto.from(sessions.viewFor(CurrentUser.id(), id));
    }

    @PatchMapping("/{id}")
    public SessionDto reschedule(@PathVariable UUID id,
                                 @RequestParam Instant at,
                                 @RequestParam(defaultValue = "0") int durationMinutes) {
        return SessionDto.from(sessions.reschedule(CurrentUser.id(), id, at, durationMinutes));
    }

    /** Pihak yang bukan pengusul menerima proposal reschedule. */
    @PostMapping("/{id}/reschedule/confirm")
    public SessionDto confirmReschedule(@PathVariable UUID id) {
        return SessionDto.from(sessions.confirmReschedule(CurrentUser.id(), id));
    }

    /** Siapapun peserta dapat menolak/membatalkan proposal reschedule. */
    @PostMapping("/{id}/reschedule/reject")
    public SessionDto rejectReschedule(@PathVariable UUID id) {
        return SessionDto.from(sessions.rejectReschedule(CurrentUser.id(), id));
    }

    /** Body: {"status": "ONGOING" | "COMPLETED" | "CANCELLED"} */
    @PostMapping("/{id}/status")
    public SessionDto setStatus(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        SessionStatus next = SessionStatus.valueOf(body.get("status"));
        return SessionDto.from(sessions.setStatus(CurrentUser.id(), id, next));
    }

    @PostMapping("/{id}/schedule/confirm")
    public SessionDto confirmSchedule(@PathVariable UUID id) {
        return SessionDto.from(sessions.confirmInitialSchedule(CurrentUser.id(), id, true));
    }

    @PostMapping("/{id}/schedule/reject")
    public SessionDto rejectSchedule(@PathVariable UUID id) {
        return SessionDto.from(sessions.confirmInitialSchedule(CurrentUser.id(), id, false));
    }

    @PostMapping("/{id}/follow-up")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionDto followup(@PathVariable UUID id, @Valid @RequestBody FollowupBody body) {
        return SessionDto.from(sessions.scheduleFollowup(CurrentUser.id(), id, body.scheduledAt(), body.durationMinutes()));
    }

    public record FollowupBody(Instant scheduledAt, int durationMinutes) {}
}
