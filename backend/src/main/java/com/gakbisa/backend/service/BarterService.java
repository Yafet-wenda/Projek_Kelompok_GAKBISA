package com.gakbisa.backend.service;

import com.gakbisa.backend.config.AppExceptions.ForbiddenException;
import com.gakbisa.backend.config.AppExceptions.NotFoundException;
import com.gakbisa.backend.config.AppExceptions.ValidationException;
import com.gakbisa.backend.domain.BarterRequest;
import com.gakbisa.backend.domain.BarterStatus;
import com.gakbisa.backend.domain.SkillType;
import com.gakbisa.backend.domain.User;
import com.gakbisa.backend.domain.UserSkill;
import com.gakbisa.backend.dto.BarterCreateRequest;
import com.gakbisa.backend.repository.BarterRequestRepository;
import com.gakbisa.backend.repository.UserRepository;
import com.gakbisa.backend.repository.UserSkillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Mengelola permintaan barter dan transisi status yang sah (proposal §3.2 no.4,
 * skenario uji §8.2 no.6): hanya status valid yang boleh dilakukan, hanya pihak
 * terkait yang boleh merespons.
 */
@Service
public class BarterService {

    private final BarterRequestRepository barter;
    private final UserRepository users;
    private final UserSkillRepository userSkills;
    private final NotificationService notifications;

    public BarterService(BarterRequestRepository barter, UserRepository users,
                         UserSkillRepository userSkills, NotificationService notifications) {
        this.barter = barter;
        this.users = users;
        this.userSkills = userSkills;
        this.notifications = notifications;
    }

    @Transactional
    public BarterRequest send(UUID requesterId, BarterCreateRequest req) {
        User requester = users.findById(requesterId).orElseThrow(() -> new NotFoundException("Pengguna tidak ditemukan"));
        if (requesterId.equals(req.targetUserId())) {
            throw new ValidationException("Tidak dapat mengirim barter ke diri sendiri");
        }
        User target = users.findById(req.targetUserId()).orElseThrow(() -> new NotFoundException("Kandidat tidak ditemukan"));

        UserSkill offered = ownedSkill(requesterId, req.offeredUserSkillId(), SkillType.OFFER);
        UserSkill wanted = ownedSkill(requesterId, req.wantedUserSkillId(), SkillType.WANT);

        // Keahlian yang ingin dipelajari harus benar-benar ditawarkan kandidat.
        boolean targetOffersWanted = userSkills.findByUserIdAndType(target.getId(), SkillType.OFFER)
            .stream().anyMatch(us -> us.getSkill().getId().equals(wanted.getSkill().getId()));
        if (!targetOffersWanted) {
            throw new ValidationException(
                "Kandidat tidak menawarkan keahlian " + wanted.getSkill().getName() + " sehingga barter tidak dapat dikirim");
        }

        boolean duplicate = barter.existsByRequesterIdAndTargetUserIdAndStatusInAndWantedUserSkill_Id(
            requesterId, target.getId(), List.of(BarterStatus.PENDING, BarterStatus.ACCEPTED), wanted.getId());
        if (duplicate) {
            throw new ValidationException("Permintaan serupa masih aktif untuk kandidat ini");
        }

        BarterRequest b = new BarterRequest();
        b.setRequester(requester);
        b.setTargetUser(target);
        b.setOfferedUserSkill(offered);
        b.setWantedUserSkill(wanted);
        b.setMessage(req.message());
        b.setStatus(BarterStatus.PENDING);
        BarterRequest saved = barter.save(b);

        notifications.push(target, NotificationService.TYPE_BARTER, "Permintaan barter baru",
            requester.getFullName() + " mengirim permintaan barter: " + wanted.getSkill().getName()
                + " <-> " + offered.getSkill().getName());
        return saved;
    }

    @Transactional
    public BarterRequest respond(UUID actorId, UUID barterId, boolean accept) {
        BarterRequest b = require(barterId);
        if (!b.getTargetUser().getId().equals(actorId)) {
            throw new ForbiddenException("Hanya penerima permintaan yang dapat merespons");
        }
        transition(b, accept ? BarterStatus.ACCEPTED : BarterStatus.REJECTED);
        notifications.push(b.getRequester(), NotificationService.TYPE_BARTER,
            accept ? "Permintaan barter diterima" : "Permintaan barter ditolak",
            b.getTargetUser().getFullName() + (accept ? " menerima" : " menolak") + " permintaanmu.");
        return barter.save(b);
    }

    @Transactional
    public BarterRequest cancel(UUID actorId, UUID barterId) {
        BarterRequest b = require(barterId);
        if (!b.getRequester().getId().equals(actorId)) {
            throw new ForbiddenException("Hanya pengirim yang dapat membatalkan permintaan");
        }
        transition(b, BarterStatus.CANCELLED);
        return barter.save(b);
    }

    /** Dipakai SessionService ketika seluruh sesi barter selesai. */
    @Transactional
    public BarterRequest complete(UUID barterId) {
        BarterRequest b = require(barterId);
        transition(b, BarterStatus.COMPLETED);
        return barter.save(b);
    }

    @Transactional(readOnly = true)
    public List<BarterRequest> inbox(UUID userId) {
        return barter.findByTargetUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<BarterRequest> outbox(UUID userId) {
        return barter.findByRequesterIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public BarterRequest require(UUID barterId) {
        return barter.findById(barterId).orElseThrow(() -> new NotFoundException("Permintaan barter tidak ditemukan"));
    }

    /** Validasi state machine — pihak eksternal memanggil lewat metode layanan ini. */
    public void transition(BarterRequest b, BarterStatus next) {
        if (!b.getStatus().canTransitionTo(next)) {
            throw new ValidationException("Status " + b.getStatus() + " tidak dapat diubah menjadi " + next);
        }
        b.setStatus(next);
    }

    private UserSkill ownedSkill(UUID userId, UUID userSkillId, SkillType expectedType) {
        UserSkill us = userSkills.findById(userSkillId)
            .orElseThrow(() -> new NotFoundException("Keahlian pengguna tidak ditemukan"));
        if (!us.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Keahlian bukan milik Anda");
        }
        if (us.getType() != expectedType) {
            throw new ValidationException("Keahlian harus bertipe " + expectedType);
        }
        return us;
    }
}
