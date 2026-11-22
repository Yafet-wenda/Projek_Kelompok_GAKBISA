package com.gakbisa.backend.service;

import com.gakbisa.backend.config.AppExceptions.ForbiddenException;
import com.gakbisa.backend.config.AppExceptions.ValidationException;
import com.gakbisa.backend.domain.*;
import com.gakbisa.backend.repository.BarterRequestRepository;
import com.gakbisa.backend.repository.LearningSessionRepository;
import com.gakbisa.backend.repository.UserRepository;
import com.gakbisa.backend.repository.UserSkillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BarterServiceTest {

    private BarterRequestRepository barter;
    private LearningSessionRepository sessions;
    private NotificationService notifications;
    private BarterService service;
    private BarterRequest parent;

    @BeforeEach
    void setup() {
        barter = mock(BarterRequestRepository.class);
        sessions = mock(LearningSessionRepository.class);
        notifications = mock(NotificationService.class);
        service = new BarterService(barter, mock(UserRepository.class), mock(UserSkillRepository.class),
            notifications, sessions);
        parent = new BarterRequest();
        parent.setId(UUID.randomUUID());
        parent.setRequester(user());
        parent.setTargetUser(user());
        parent.setStatus(BarterStatus.ACCEPTED);
        when(barter.findByIdForUpdate(parent.getId())).thenReturn(Optional.of(parent));
        when(barter.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(barter.save(any(BarterRequest.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @ParameterizedTest
    @EnumSource(value = BarterStatus.class, names = {"PENDING", "ACCEPTED"})
    void requesterCancellationCancelsBothActiveStatesAndPreservesHistory(BarterStatus status) {
        parent.setStatus(status);
        LearningSession scheduled = session(SessionStatus.SCHEDULED);
        LearningSession ongoing = session(SessionStatus.ONGOING);
        LearningSession completed = session(SessionStatus.COMPLETED);
        LearningSession cancelled = session(SessionStatus.CANCELLED);
        Instant completionTime = Instant.parse("2026-01-01T10:00:00Z");
        completed.setCompletedAt(completionTime);
        Instant scheduledTime = scheduled.getScheduledAt();
        when(sessions.findByBarterRequestId(parent.getId()))
            .thenReturn(List.of(scheduled, ongoing, completed, cancelled));

        assertSame(parent, service.cancel(parent.getRequester().getId(), parent.getId()));

        assertEquals(BarterStatus.CANCELLED, parent.getStatus());
        assertEquals(SessionStatus.CANCELLED, scheduled.getStatus());
        assertEquals(SessionStatus.CANCELLED, ongoing.getStatus());
        assertEquals(SessionStatus.COMPLETED, completed.getStatus());
        assertEquals(completionTime, completed.getCompletedAt());
        assertEquals(SessionStatus.CANCELLED, cancelled.getStatus());
        assertEquals(scheduledTime, scheduled.getScheduledAt());
        assertEquals(60, scheduled.getDurationMinutes());
        assertEquals("gakbisa-" + scheduled.getId(), scheduled.getMeetingRoom());
        assertNull(scheduled.getCompletedAt());
        assertNull(ongoing.getCompletedAt());
        var order = inOrder(barter, sessions);
        order.verify(barter).findByIdForUpdate(parent.getId());
        order.verify(barter).findById(parent.getId());
        order.verify(sessions).findByBarterRequestId(parent.getId());
        order.verify(sessions).saveAll(List.of(scheduled, ongoing));
        order.verify(barter).save(parent);
        verifyNoMoreInteractions(sessions);
        verifyNoInteractions(notifications);
    }

    @Test
    void targetAndOutsiderCannotCancelOrMutateChildren() {
        LearningSession scheduled = session(SessionStatus.SCHEDULED);
        for (UUID actor : List.of(parent.getTargetUser().getId(), UUID.randomUUID())) {
            assertThrows(ForbiddenException.class, () -> service.cancel(actor, parent.getId()));
        }
        assertEquals(BarterStatus.ACCEPTED, parent.getStatus());
        assertEquals(SessionStatus.SCHEDULED, scheduled.getStatus());
        verify(barter, never()).save(any());
        verifyNoInteractions(sessions, notifications);
    }

    @ParameterizedTest
    @EnumSource(value = BarterStatus.class, names = {"CANCELLED", "REJECTED", "COMPLETED"})
    void terminalParentRejectsCancellationWithoutMutatingChildren(BarterStatus status) {
        parent.setStatus(status);
        LearningSession ongoing = session(SessionStatus.ONGOING);

        assertThrows(ValidationException.class,
            () -> service.cancel(parent.getRequester().getId(), parent.getId()));

        assertEquals(status, parent.getStatus());
        assertEquals(SessionStatus.ONGOING, ongoing.getStatus());
        verify(barter, never()).save(any());
        verifyNoInteractions(sessions, notifications);
    }

    @Test
    void respondLocksParentBeforeAccepting() {
        parent.setStatus(BarterStatus.PENDING);

        service.respond(parent.getTargetUser().getId(), parent.getId(), true);

        assertEquals(BarterStatus.ACCEPTED, parent.getStatus());
        var order = inOrder(barter);
        order.verify(barter).findByIdForUpdate(parent.getId());
        order.verify(barter).findById(parent.getId());
        order.verify(barter).save(parent);
    }

    @Test
    void respondLocksParentBeforeRejecting() {
        parent.setStatus(BarterStatus.PENDING);

        service.respond(parent.getTargetUser().getId(), parent.getId(), false);

        assertEquals(BarterStatus.REJECTED, parent.getStatus());
        verify(barter).findByIdForUpdate(parent.getId());
        verifyNoInteractions(sessions);
    }

    @Test
    void onlyTargetCanRespond() {
        parent.setStatus(BarterStatus.PENDING);
        for (UUID actor : List.of(parent.getRequester().getId(), UUID.randomUUID())) {
            assertThrows(ForbiddenException.class, () -> service.respond(actor, parent.getId(), true));
        }
        assertEquals(BarterStatus.PENDING, parent.getStatus());
        verify(barter, never()).save(any());
        verifyNoInteractions(sessions, notifications);
    }

    @ParameterizedTest
    @EnumSource(value = BarterStatus.class, names = {"ACCEPTED", "CANCELLED", "REJECTED", "COMPLETED"})
    void nonPendingParentCannotRespond(BarterStatus status) {
        parent.setStatus(status);
        for (boolean accept : List.of(true, false)) {
            assertThrows(ValidationException.class,
                () -> service.respond(parent.getTargetUser().getId(), parent.getId(), accept));
        }
        assertEquals(status, parent.getStatus());
        verify(barter, never()).save(any());
        verifyNoInteractions(sessions, notifications);
    }

    @Test
    void completeLocksParentBeforeTransition() {
        service.complete(parent.getId());

        assertEquals(BarterStatus.COMPLETED, parent.getStatus());
        var order = inOrder(barter);
        order.verify(barter).findByIdForUpdate(parent.getId());
        order.verify(barter).findById(parent.getId());
        order.verify(barter).save(parent);
    }

    @ParameterizedTest
    @EnumSource(value = BarterStatus.class, names = {"PENDING", "CANCELLED", "REJECTED", "COMPLETED"})
    void nonAcceptedParentCannotComplete(BarterStatus status) {
        parent.setStatus(status);

        assertThrows(ValidationException.class, () -> service.complete(parent.getId()));

        assertEquals(status, parent.getStatus());
        verify(barter, never()).save(any());
        verifyNoInteractions(sessions, notifications);
    }

    private LearningSession session(SessionStatus status) {
        LearningSession s = new LearningSession();
        s.setId(UUID.randomUUID());
        s.setBarterRequest(parent);
        s.setStatus(status);
        s.setScheduledAt(Instant.now());
        s.setDurationMinutes(60);
        s.setMeetingRoom("gakbisa-" + s.getId());
        return s;
    }

    private User user() {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setFullName("Participant");
        return u;
    }
}
