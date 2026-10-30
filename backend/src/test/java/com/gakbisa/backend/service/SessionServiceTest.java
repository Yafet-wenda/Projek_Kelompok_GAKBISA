package com.gakbisa.backend.service;

import com.gakbisa.backend.config.AppExceptions.ForbiddenException;
import com.gakbisa.backend.config.AppExceptions.ValidationException;
import com.gakbisa.backend.domain.*;
import com.gakbisa.backend.dto.SessionCreateRequest;
import com.gakbisa.backend.repository.BarterRequestRepository;
import com.gakbisa.backend.repository.LearningSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Skenario uji §8.2 no.7 & no.8: sesi ditolak sebelum diterima; akses khusus peserta. */
class SessionServiceTest {

    private LearningSessionRepository sessions;
    private BarterRequestRepository barter;
    private BarterService barterService;
    private NotificationService notifications;
    private SessionService service;

    private User requester;
    private User target;
    private BarterRequest accepted;
    private BarterRequest pending;

    @BeforeEach
    void setup() {
        sessions = mock(LearningSessionRepository.class);
        barter = mock(BarterRequestRepository.class);
        barterService = mock(BarterService.class);
        notifications = mock(NotificationService.class);
        service = new SessionService(sessions, barter, barterService, notifications);

        requester = user("Rina");
        target = user("Tono");

        accepted = barterOf(BarterStatus.ACCEPTED);
        pending = barterOf(BarterStatus.PENDING);
        when(sessions.save(any(LearningSession.class))).thenAnswer(inv -> {
            LearningSession s = inv.getArgument(0);
            if (s.getId() == null) s.setId(UUID.randomUUID());
            return s;
        });
    }

    private BarterRequest barterOf(BarterStatus status) {
        BarterRequest b = new BarterRequest();
        b.setId(UUID.randomUUID());
        b.setRequester(requester);
        b.setTargetUser(target);
        b.setStatus(status);
        UserSkill wanted = new UserSkill();
        wanted.setSkill(skill("Java"));
        b.setWantedUserSkill(wanted);
        return b;
    }

    @Test
    void sesiDitolakSaatBarterBelumditerima() {
        when(barter.findById(pending.getId())).thenReturn(Optional.of(pending));

        var req = new SessionCreateRequest(pending.getId(), Instant.now().plus(1, ChronoUnit.DAYS), 60, null);
        var ex = assertThrows(ValidationException.class, () -> service.schedule(requester.getId(), req));
        assertTrue(ex.getMessage().contains("ACCEPTED"));
        verify(sessions, never()).save(any());
    }

    @Test
    void sesiDitolakUntukBukanPeserta() {
        when(barter.findById(accepted.getId())).thenReturn(Optional.of(accepted));

        var req = new SessionCreateRequest(accepted.getId(), Instant.now().plus(1, ChronoUnit.DAYS), 60, null);
        assertThrows(ForbiddenException.class, () -> service.schedule(user("Orang asing").getId(), req));
    }

    @Test
    void ruangJitsiAcakDanUnik() {
        when(barter.findById(accepted.getId())).thenReturn(Optional.of(accepted));

        var req = new SessionCreateRequest(accepted.getId(), Instant.now().plus(1, ChronoUnit.DAYS), 60, null);
        LearningSession s1 = service.schedule(requester.getId(), req);
        LearningSession s2 = service.schedule(requester.getId(), req);

        assertTrue(s1.getMeetingRoom().startsWith("gakbisa-"));
        assertNotEquals(s1.getMeetingRoom(), s2.getMeetingRoom(), "room id tidak boleh dapat ditebak/duplikat");
        assertTrue(s1.getMeetingUrl().contains(s1.getMeetingRoom()));
    }

    @Test
    void sesiTerakhirSelesaiIkutMenyelesaikanBarter() {
        LearningSession s = sessionOf(accepted, SessionStatus.ONGOING);
        when(sessions.findById(s.getId())).thenReturn(Optional.of(s));
        when(sessions.findByBarterRequestId(accepted.getId())).thenReturn(List.of(s));

        service.setStatus(requester.getId(), s.getId(), SessionStatus.COMPLETED);

        verify(barterService).complete(accepted.getId());
    }

    @Test
    void barterTetapTerbukaBilaMasihAdaSesiBelumSelesai() {
        LearningSession s = sessionOf(accepted, SessionStatus.ONGOING);
        LearningSession lain = sessionOf(accepted, SessionStatus.SCHEDULED);
        when(sessions.findById(s.getId())).thenReturn(Optional.of(s));
        when(sessions.findByBarterRequestId(accepted.getId())).thenReturn(List.of(s, lain));

        service.setStatus(requester.getId(), s.getId(), SessionStatus.COMPLETED);

        assertEquals(BarterStatus.ACCEPTED, accepted.getStatus());
        verify(barterService, never()).complete(any());
    }

    private LearningSession sessionOf(BarterRequest b, SessionStatus status) {
        LearningSession s = new LearningSession();
        s.setId(UUID.randomUUID());
        s.setBarterRequest(b);
        s.setStatus(status);
        s.setScheduledAt(Instant.now());
        s.setDurationMinutes(60);
        s.setMeetingProvider(MeetingProvider.JITSI);
        s.setMeetingRoom("gakbisa-x");
        return s;
    }

    private static User user(String name) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setFullName(name);
        u.setEmail(name.toLowerCase().replace(" ", "") + "@gakbisa.test");
        u.setPasswordHash("x");
        u.setRole(UserRole.STUDENT);
        u.setStatus(AccountStatus.ACTIVE);
        return u;
    }

    private static Skill skill(String name) {
        Skill s = new Skill();
        s.setId(UUID.randomUUID());
        s.setName(name);
        return s;
    }
}
