package com.gakbisa.backend.repository;

import com.gakbisa.backend.domain.LearningSession;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LearningSessionRepository extends JpaRepository<LearningSession, UUID> {

    // Halaman sesi membaca partner dan nama keahlian lewat barterRequest. Semua mata
    // rantai itu LAZY dan open-in-view dimatikan, jadi harus ikut termuat di sini.
    @EntityGraph(attributePaths = {"barterRequest", "barterRequest.requester", "barterRequest.targetUser",
        "barterRequest.offeredUserSkill", "barterRequest.offeredUserSkill.skill",
        "barterRequest.wantedUserSkill", "barterRequest.wantedUserSkill.skill"})
    Optional<LearningSession> findById(UUID id);

    List<LearningSession> findByBarterRequestId(UUID barterRequestId);

    @EntityGraph(attributePaths = {"barterRequest", "barterRequest.requester", "barterRequest.targetUser",
        "barterRequest.offeredUserSkill", "barterRequest.offeredUserSkill.skill",
        "barterRequest.wantedUserSkill", "barterRequest.wantedUserSkill.skill"})
    @Query("""
        select s from LearningSession s
        join s.barterRequest b
        where b.requester.id = :userId or b.targetUser.id = :userId
        order by s.scheduledAt desc
        """)
    List<LearningSession> findForParticipant(@Param("userId") UUID userId);

    long countByStatus(com.gakbisa.backend.domain.SessionStatus status);
}
