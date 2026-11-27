package com.gakbisa.backend.service;

import com.gakbisa.backend.config.AppExceptions.ValidationException;
import com.gakbisa.backend.domain.AccountStatus;
import com.gakbisa.backend.domain.BarterAgreement;
import com.gakbisa.backend.domain.BarterRequest;
import com.gakbisa.backend.domain.BarterStatus;
import com.gakbisa.backend.domain.User;
import com.gakbisa.backend.domain.UserRole;
import com.gakbisa.backend.repository.BarterAgreementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

class BarterAgreementServiceTest {
    private BarterAgreementRepository agreements;
    private BarterService barters;
    private JdbcTemplate jdbc;
    private NotificationService notifications;
    private BarterAgreementService service;
    private final UUID barterId = UUID.randomUUID();
    private final User requester = user("requester");
    private final User target = user("target");
    private final BarterRequest barter = new BarterRequest();

    @BeforeEach
    void setup() {
        agreements = mock(BarterAgreementRepository.class);
        barters = mock(BarterService.class);
        jdbc = mock(JdbcTemplate.class);
        notifications = mock(NotificationService.class);
        service = new BarterAgreementService(agreements, barters, jdbc, notifications);

        barter.setId(barterId);
        barter.setRequester(requester);
        barter.setTargetUser(target);
        barter.setStatus(BarterStatus.ACCEPTED);
        when(barters.viewFor(any(), eq(barterId))).thenReturn(barter);
        when(jdbc.queryForObject(startsWith("SELECT id FROM barter_requests"), eq(UUID.class), eq(barterId)))
            .thenReturn(barterId);
        when(jdbc.queryForObject(startsWith("SELECT EXISTS"), eq(Boolean.class), eq(barterId)))
            .thenReturn(false);
        when(agreements.save(any(BarterAgreement.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void proposalDicatatSebagaiPersetujuanPengusulSaja() {
        when(agreements.findByBarterRequestId(barterId)).thenReturn(Optional.empty());

        var result = service.propose(requester.getId(), barterId, 60, 4);

        assertEquals(60, result.durationMinutes());
        assertEquals(4, result.sessionCount());
        assertEquals(requester.getId(), result.proposedBy());
        assertNotNull(result.requesterAcceptedAt());
        assertNull(result.targetAcceptedAt());
        assertFalse(result.acceptedByBoth());
    }

    @Test
    void pihakKeduaMenerimaDanKesepakatanMenjadiAktif() {
        BarterAgreement agreement = agreement();
        when(agreements.findByBarterRequestId(barterId)).thenReturn(Optional.of(agreement));

        var result = service.accept(target.getId(), barterId);

        assertTrue(result.acceptedByBoth());
        assertNotNull(result.requesterAcceptedAt());
        assertNotNull(result.targetAcceptedAt());
    }

    @Test
    void kesepakatanTidakDapatDiubahSaatAdaSesiAktif() {
        when(jdbc.queryForObject(startsWith("SELECT EXISTS"), eq(Boolean.class), eq(barterId)))
            .thenReturn(true);

        assertThrows(ValidationException.class,
            () -> service.propose(requester.getId(), barterId, 60, 4));
        verify(agreements, never()).save(any());
    }

    @Test
    void durasiDanJumlahSesiDivalidasi() {
        assertThrows(ValidationException.class,
            () -> service.propose(requester.getId(), barterId, 10, 4));
        assertThrows(ValidationException.class,
            () -> service.propose(requester.getId(), barterId, 60, 0));
        verify(jdbc, never()).queryForObject(startsWith("SELECT id FROM barter_requests"), eq(UUID.class), eq(barterId));
    }

    private BarterAgreement agreement() {
        BarterAgreement agreement = new BarterAgreement();
        agreement.setBarterRequest(barter);
        agreement.setDurationMinutes(60);
        agreement.setSessionCount(4);
        agreement.setProposedBy(requester);
        agreement.setRequesterAcceptedAt(java.time.Instant.now());
        return agreement;
    }

    private static User user(String name) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFullName(name);
        user.setEmail(name + "@example.test");
        user.setPasswordHash("x");
        user.setRole(UserRole.STUDENT);
        user.setStatus(AccountStatus.ACTIVE);
        return user;
    }
}
