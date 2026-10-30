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

    @EntityGraph(attributePaths = {"barterRequest", "barterRequest.requester", "barterRequest.targetUser"})
    Optional<LearningSession> findById(UUID id);

    List<LearningSession> findByBarterRequestId(UUID barterRequestId);

    @EntityGraph(attributePaths = {"barterRequest", "barterRequest.requester", "barterRequest.targetUser"})
    @Query("""
        select s from LearningSession s
        join s.barterRequest b
        where b.requester.id = :userId or b.targetUser.id = :userId
        order by s.scheduledAt desc
        """)
    List<LearningSession> findForParticipant(@Param("userId") UUID userId);

    long countByStatus(com.gakbisa.backend.domain.SessionStatus status);
}
