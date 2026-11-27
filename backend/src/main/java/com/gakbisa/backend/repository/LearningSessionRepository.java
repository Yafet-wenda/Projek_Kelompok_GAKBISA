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
        "barterRequest.wantedUserSkill", "barterRequest.wantedUserSkill.skill",
        "rescheduleProposedBy"})
    Optional<LearningSession> findById(UUID id);

    List<LearningSession> findByBarterRequestId(UUID barterRequestId);

    @EntityGraph(attributePaths = {"barterRequest", "barterRequest.requester", "barterRequest.targetUser",
        "barterRequest.offeredUserSkill", "barterRequest.offeredUserSkill.skill",
        "barterRequest.wantedUserSkill", "barterRequest.wantedUserSkill.skill",
        "rescheduleProposedBy"})
    @Query("""
        select s from LearningSession s
        join s.barterRequest b
        where b.requester.id = :userId or b.targetUser.id = :userId
        order by s.scheduledAt desc
        """)
    List<LearningSession> findForParticipant(@Param("userId") UUID userId);

    long countByStatus(com.gakbisa.backend.domain.SessionStatus status);

    @Query(value = """
        SELECT count(DISTINCT s.id)
        FROM learning_sessions s
        LEFT JOIN barter_requests b ON b.id = s.barter_request_id
        LEFT JOIN group_members gm ON gm.group_id = s.group_id AND gm.user_id = :userId AND gm.status = 'ACTIVE'
        WHERE s.status = 'COMPLETED'
          AND (b.requester_id = :userId OR b.target_user_id = :userId OR gm.user_id = :userId)
        """, nativeQuery = true)
    long countCompletedForUser(@Param("userId") UUID userId);
}
