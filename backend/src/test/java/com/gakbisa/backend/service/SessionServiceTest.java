package com.gakbisa.backend.service;

import com.gakbisa.backend.config.AppExceptions.ForbiddenException;
import com.gakbisa.backend.config.AppExceptions.ValidationException;
import com.gakbisa.backend.domain.*;
import com.gakbisa.backend.dto.SessionCreateRequest;
import com.gakbisa.backend.repository.BarterRequestRepository;
import com.gakbisa.backend.repository.LearningSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

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
        when(barter.findByIdForUpdate(pending.getId())).thenReturn(Optional.of(pending));

        var req = new SessionCreateRequest(pending.getId(), Instant.now().plus(1, ChronoUnit.DAYS), 60, null);
        var ex = assertThrows(ValidationException.class, () -> service.schedule(requester.getId(), req));
        assertTrue(ex.getMessage().contains("ACCEPTED"));
        verify(sessions, never()).save(any());
    }

    @Test
    void sesiDitolakUntukBukanPeserta() {
        when(barter.findByIdForUpdate(accepted.getId())).thenReturn(Optional.of(accepted));

        var req = new SessionCreateRequest(accepted.getId(), Instant.now().plus(1, ChronoUnit.DAYS), 60, null);
        assertThrows(ForbiddenException.class, () -> service.schedule(user("Orang asing").getId(), req));
    }

    @Test
    void ruangJitsiAcakDanUnik() {
        when(barter.findByIdForUpdate(accepted.getId())).thenReturn(Optional.of(accepted));

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
        stubSessionMutation(s);
        when(sessions.findByBarterRequestId(accepted.getId())).thenReturn(List.of(s));

        service.setStatus(requester.getId(), s.getId(), SessionStatus.COMPLETED);

        verify(barterService).complete(accepted.getId());
    }

    @Test
    void barterTetapTerbukaBilaMasihAdaSesiBelumSelesai() {
        LearningSession s = sessionOf(accepted, SessionStatus.ONGOING);
        LearningSession lain = sessionOf(accepted, SessionStatus.SCHEDULED);
        stubSessionMutation(s);
        when(sessions.findByBarterRequestId(accepted.getId())).thenReturn(List.of(s, lain));

        service.setStatus(requester.getId(), s.getId(), SessionStatus.COMPLETED);

        assertEquals(BarterStatus.ACCEPTED, accepted.getStatus());
        verify(barterService, never()).complete(any());
    }

    @Test
    void completeAThenCancelBReconcilesBarter() {
        LearningSession a = sessionOf(accepted, SessionStatus.ONGOING);
        LearningSession b = sessionOf(accepted, SessionStatus.SCHEDULED);
        stubSessionMutation(a);
        stubSessionMutation(b);
        when(sessions.findByBarterRequestId(accepted.getId())).thenReturn(List.of(a, b));

        service.setStatus(requester.getId(), a.getId(), SessionStatus.COMPLETED);
        Instant completedAt = a.getCompletedAt();
        verify(barterService, never()).complete(any());

        service.setStatus(target.getId(), b.getId(), SessionStatus.CANCELLED);

        assertEquals(SessionStatus.COMPLETED, a.getStatus());
        assertEquals(completedAt, a.getCompletedAt());
        assertEquals(SessionStatus.CANCELLED, b.getStatus());
        verify(barterService).complete(accepted.getId());
    }

    @Test
    void allCancelledKeepsBarterAcceptedAndAllowsNewSchedule() {
        LearningSession s = sessionOf(accepted, SessionStatus.SCHEDULED);
        LearningSession cancelled = sessionOf(accepted, SessionStatus.CANCELLED);
        stubSessionMutation(s);
        when(sessions.findByBarterRequestId(accepted.getId())).thenReturn(List.of(s, cancelled));

        service.setStatus(target.getId(), s.getId(), SessionStatus.CANCELLED);

        assertEquals(BarterStatus.ACCEPTED, accepted.getStatus());
        verify(barterService, never()).complete(any());
        var req = new SessionCreateRequest(accepted.getId(), Instant.now().plus(1, ChronoUnit.DAYS), 60, null);
        assertEquals(SessionStatus.SCHEDULED, service.schedule(target.getId(), req).getStatus());
    }

    @Test
    void cancellationWithCompletedHistoryStillWaitsForActiveSibling() {
        LearningSession cancelled = sessionOf(accepted, SessionStatus.SCHEDULED);
        LearningSession active = sessionOf(accepted, SessionStatus.ONGOING);
        LearningSession completed = sessionOf(accepted, SessionStatus.COMPLETED);
        stubSessionMutation(cancelled);
        when(sessions.findByBarterRequestId(accepted.getId())).thenReturn(List.of(cancelled, active, completed));

        service.setStatus(requester.getId(), cancelled.getId(), SessionStatus.CANCELLED);

        assertEquals(BarterStatus.ACCEPTED, accepted.getStatus());
        verify(barterService, never()).complete(any());
    }

    @ParameterizedTest
    @EnumSource(value = BarterStatus.class, names = {"PENDING", "REJECTED", "CANCELLED", "COMPLETED"})
    void closedOrUnacceptedParentCannotScheduleOrResume(BarterStatus status) {
        BarterRequest parent = barterOf(status);
        LearningSession s = sessionOf(parent, SessionStatus.SCHEDULED);
        stubSessionMutation(s);
        Instant originalTime = s.getScheduledAt();
        Instant newTime = Instant.now().plus(1, ChronoUnit.DAYS);
        var req = new SessionCreateRequest(parent.getId(), newTime, 30, null);

        assertThrows(ValidationException.class, () -> service.schedule(requester.getId(), req));
        assertThrows(ValidationException.class, () -> service.reschedule(requester.getId(), s.getId(), newTime, 30));
        assertThrows(ValidationException.class, () -> service.setStatus(requester.getId(), s.getId(), SessionStatus.ONGOING));
        assertThrows(ValidationException.class, () -> service.setStatus(target.getId(), s.getId(), SessionStatus.COMPLETED));

        assertEquals(status, parent.getStatus());
        assertEquals(SessionStatus.SCHEDULED, s.getStatus());
        assertEquals(originalTime, s.getScheduledAt());
        assertEquals(60, s.getDurationMinutes());
        assertNull(s.getCompletedAt());
        verify(sessions, never()).save(any());
        verifyNoInteractions(barterService, notifications);
    }

    @Test
    void ongoingSessionOnCancelledParentCannotComplete() {
        BarterRequest parent = barterOf(BarterStatus.CANCELLED);
        LearningSession s = sessionOf(parent, SessionStatus.ONGOING);
        stubSessionMutation(s);

        assertThrows(ValidationException.class,
            () -> service.setStatus(requester.getId(), s.getId(), SessionStatus.COMPLETED));

        assertEquals(SessionStatus.ONGOING, s.getStatus());
        assertNull(s.getCompletedAt());
        verify(sessions, never()).save(any());
        verifyNoInteractions(barterService, notifications);
    }

    @Test
    void nonParticipantCannotMutateSession() {
        LearningSession s = sessionOf(accepted, SessionStatus.SCHEDULED);
        stubSessionMutation(s);
        UUID outsider = UUID.randomUUID();
        Instant originalTime = s.getScheduledAt();

        assertThrows(ForbiddenException.class,
            () -> service.reschedule(outsider, s.getId(), Instant.now().plus(1, ChronoUnit.DAYS), 30));
        for (SessionStatus next : List.of(SessionStatus.ONGOING, SessionStatus.COMPLETED, SessionStatus.CANCELLED)) {
            assertThrows(ForbiddenException.class, () -> service.setStatus(outsider, s.getId(), next));
        }

        assertEquals(SessionStatus.SCHEDULED, s.getStatus());
        assertEquals(originalTime, s.getScheduledAt());
        assertEquals(60, s.getDurationMinutes());
        assertNull(s.getCompletedAt());
        verify(sessions, never()).save(any());
        verifyNoInteractions(barterService, notifications);
    }

    @ParameterizedTest
    @EnumSource(value = SessionStatus.class, names = {"CANCELLED", "COMPLETED"})
    void terminalSessionCannotResume(SessionStatus status) {
        LearningSession s = sessionOf(accepted, status);
        Instant completedAt = status == SessionStatus.COMPLETED ? Instant.now().minusSeconds(120) : null;
        s.setCompletedAt(completedAt);
        stubSessionMutation(s);
        Instant originalTime = s.getScheduledAt();

        assertThrows(ValidationException.class,
            () -> service.reschedule(requester.getId(), s.getId(), Instant.now().plusSeconds(60), 30));
        assertThrows(ValidationException.class,
            () -> service.setStatus(requester.getId(), s.getId(), SessionStatus.ONGOING));
        assertThrows(ValidationException.class,
            () -> service.setStatus(requester.getId(), s.getId(), SessionStatus.COMPLETED));
        if (status == SessionStatus.COMPLETED) {
            assertThrows(ValidationException.class,
                () -> service.setStatus(requester.getId(), s.getId(), SessionStatus.CANCELLED));
        }

        assertEquals(status, s.getStatus());
        assertEquals(completedAt, s.getCompletedAt());
        assertEquals(originalTime, s.getScheduledAt());
        verify(sessions, never()).save(any());
        verifyNoInteractions(barterService, notifications);
    }

    @Test
    void rescheduleLocksParentBeforeLoadingSession() {
        LearningSession s = sessionOf(accepted, SessionStatus.SCHEDULED);
        stubSessionMutation(s);
        Instant newTime = Instant.now().plus(1, ChronoUnit.DAYS);

        service.reschedule(target.getId(), s.getId(), newTime, 30);

        var order = inOrder(sessions, barter);
        order.verify(sessions).findBarterRequestIdById(s.getId());
        order.verify(barter).findByIdForUpdate(accepted.getId());
        order.verify(sessions).findById(s.getId());
        order.verify(sessions).save(s);
        assertEquals(newTime, s.getScheduledAt());
        assertEquals(30, s.getDurationMinutes());
    }

    @ParameterizedTest
    @EnumSource(value = SessionStatus.class, names = {"ONGOING", "COMPLETED", "CANCELLED"})
    void statusMutationLocksParentBeforeLoadingSession(SessionStatus next) {
        LearningSession s = sessionOf(accepted, SessionStatus.SCHEDULED);
        stubSessionMutation(s);
        when(sessions.findByBarterRequestId(accepted.getId())).thenReturn(List.of(s));

        service.setStatus(target.getId(), s.getId(), next);

        var order = inOrder(sessions, barter);
        order.verify(sessions).findBarterRequestIdById(s.getId());
        order.verify(barter).findByIdForUpdate(accepted.getId());
        order.verify(sessions).findById(s.getId());
        order.verify(sessions).save(s);
        assertEquals(next, s.getStatus());
    }

    private void stubSessionMutation(LearningSession s) {
        when(sessions.findBarterRequestIdById(s.getId())).thenReturn(Optional.of(s.getBarterRequest().getId()));
        when(barter.findByIdForUpdate(s.getBarterRequest().getId())).thenReturn(Optional.of(s.getBarterRequest()));
        when(sessions.findById(s.getId())).thenReturn(Optional.of(s));
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
