package com.gakbisa.backend.controller;

import com.gakbisa.backend.service.JitsiMeetingService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** GET /api/sessions/{id}/meeting: akses ruang meeting, hanya untuk peserta sesi. */
@RestController
@RequestMapping("/api/sessions")
public class MeetingController {

    private final JitsiMeetingService meetings;

    public MeetingController(JitsiMeetingService meetings) {
        this.meetings = meetings;
    }

    @GetMapping("/{id}/meeting")
    public ResponseEntity<JitsiMeetingService.MeetingAccess> meeting(@PathVariable UUID id) {
        // Berisi JWT, jadi jangan pernah di-cache browser atau proxy.
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(meetings.accessFor(CurrentUser.id(), id));
    }
}
