package com.gakbisa.backend.repository;

import com.gakbisa.backend.domain.BarterRequest;
import com.gakbisa.backend.domain.BarterStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BarterRequestRepository extends JpaRepository<BarterRequest, UUID> {

    @EntityGraph(attributePaths = {"requester", "targetUser", "offeredUserSkill.skill", "wantedUserSkill.skill"})
    List<BarterRequest> findByTargetUserIdOrderByCreatedAtDesc(UUID targetUserId);

    @EntityGraph(attributePaths = {"requester", "targetUser", "offeredUserSkill.skill", "wantedUserSkill.skill"})
    List<BarterRequest> findByRequesterIdOrderByCreatedAtDesc(UUID requesterId);

    @EntityGraph(attributePaths = {"requester", "targetUser",
        "offeredUserSkill.skill", "wantedUserSkill.skill"})
    @Query("select b from BarterRequest b where b.id = :id")
    Optional<BarterRequest> findById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from BarterRequest b where b.id = :id")
    Optional<BarterRequest> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByRequesterIdAndTargetUserIdAndStatusInAndWantedUserSkill_Id(
        UUID requesterId, UUID targetUserId, List<BarterStatus> statuses, UUID wantedUserSkillId);
}
