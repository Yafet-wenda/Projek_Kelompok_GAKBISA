package com.gakbisa.backend.repository;

import com.gakbisa.backend.domain.BarterRequest;
import com.gakbisa.backend.domain.BarterStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

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
    Optional<BarterRequest> findById(UUID id);

    boolean existsByRequesterIdAndTargetUserIdAndStatusInAndWantedUserSkill_Id(
        UUID requesterId, UUID targetUserId, List<BarterStatus> statuses, UUID wantedUserSkillId);
}
