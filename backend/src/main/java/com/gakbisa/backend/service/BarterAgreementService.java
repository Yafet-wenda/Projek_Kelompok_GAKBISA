package com.gakbisa.backend.service;

import com.gakbisa.backend.config.AppExceptions.ValidationException;
import com.gakbisa.backend.domain.BarterAgreement;
import com.gakbisa.backend.domain.BarterRequest;
import com.gakbisa.backend.dto.BarterAgreementStatusDto;
import com.gakbisa.backend.repository.BarterAgreementRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class BarterAgreementService {
    private final BarterAgreementRepository agreements;
    private final BarterService barters;
    private final JdbcTemplate jdbc;
    private final NotificationService notifications;

    public BarterAgreementService(BarterAgreementRepository agreements, BarterService barters,
                                  JdbcTemplate jdbc, NotificationService notifications) {
        this.agreements = agreements;
        this.barters = barters;
        this.jdbc = jdbc;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public BarterAgreementStatusDto view(UUID actorId, UUID barterId) {
        barters.viewFor(actorId, barterId);
        return agreements.findByBarterRequestId(barterId).map(BarterAgreementStatusDto::from).orElse(null);
    }

    @Transactional
    public BarterAgreementStatusDto propose(UUID actorId, UUID barterId, int durationMinutes, int sessionCount) {
        BarterRequest barter = barters.viewFor(actorId, barterId);
        requireAcceptedBarter(barter);
        if (durationMinutes < 15 || durationMinutes > 240) {
            throw new ValidationException("Durasi sesi harus 15 sampai 240 menit");
        }
        if (sessionCount < 1 || sessionCount > 50) {
            throw new ValidationException("Jumlah sesi harus 1 sampai 50");
        }
        lockBarter(barterId);
        if (hasUnfinishedSessions(barterId)) {
            throw new ValidationException("Kesepakatan dapat diperbarui setelah sesi aktif selesai atau dibatalkan");
        }

        Instant now = Instant.now();
        BarterAgreement agreement = agreements.findByBarterRequestId(barterId).orElseGet(BarterAgreement::new);
        agreement.setBarterRequest(barter);
        agreement.setDurationMinutes(durationMinutes);
        agreement.setSessionCount(sessionCount);
        agreement.setProposedBy(barter.getRequester().getId().equals(actorId)
            ? barter.getRequester() : barter.getTargetUser());
        agreement.setRequesterAcceptedAt(barter.getRequester().getId().equals(actorId) ? now : null);
        agreement.setTargetAcceptedAt(barter.getTargetUser().getId().equals(actorId) ? now : null);
        BarterAgreementStatusDto result = BarterAgreementStatusDto.from(agreements.save(agreement));

        var partner = barter.getRequester().getId().equals(actorId) ? barter.getTargetUser() : barter.getRequester();
        notifications.push(partner, NotificationService.TYPE_BARTER, "Usulan kesepakatan barter",
            "Partner mengusulkan durasi " + durationMinutes + " menit untuk " + sessionCount + " sesi. Tinjau dan setujui di halaman barter.");
        return result;
    }

    @Transactional
    public BarterAgreementStatusDto accept(UUID actorId, UUID barterId) {
        BarterRequest barter = barters.viewFor(actorId, barterId);
        requireAcceptedBarter(barter);
        lockBarter(barterId);
        if (hasUnfinishedSessions(barterId)) {
            throw new ValidationException("Kesepakatan dapat diperbarui setelah sesi aktif selesai atau dibatalkan");
        }

        BarterAgreement agreement = agreements.findByBarterRequestId(barterId)
            .orElseThrow(() -> new ValidationException("Belum ada usulan kesepakatan untuk disetujui"));
        Instant now = Instant.now();
        if (barter.getRequester().getId().equals(actorId)) agreement.setRequesterAcceptedAt(now);
        else agreement.setTargetAcceptedAt(now);
        BarterAgreementStatusDto result = BarterAgreementStatusDto.from(agreements.save(agreement));

        var partner = barter.getRequester().getId().equals(actorId) ? barter.getTargetUser() : barter.getRequester();
        notifications.push(partner, NotificationService.TYPE_BARTER, "Kesepakatan barter diperbarui",
            result.acceptedByBoth() ? "Kedua pihak menyetujui kesepakatan. Sesi dapat dijadwalkan." : "Partner menyetujui kesepakatan barter.");
        return result;
    }

    private void requireAcceptedBarter(BarterRequest barter) {
        if (barter.getStatus() != com.gakbisa.backend.domain.BarterStatus.ACCEPTED) {
            throw new ValidationException("Kesepakatan hanya dapat dibuat untuk barter yang diterima");
        }
    }

    private void lockBarter(UUID barterId) {
        jdbc.queryForObject("SELECT id FROM barter_requests WHERE id = ? FOR UPDATE", UUID.class, barterId);
    }

    private boolean hasUnfinishedSessions(UUID barterId) {
        return jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM learning_sessions WHERE barter_request_id = ? AND status IN ('SCHEDULED','ONGOING','PENDING_RESCHEDULE'))",
            Boolean.class, barterId);
    }
}
