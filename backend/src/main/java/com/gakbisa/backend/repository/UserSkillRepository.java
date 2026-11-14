package com.gakbisa.backend.repository;

import com.gakbisa.backend.domain.SkillType;
import com.gakbisa.backend.domain.UserSkill;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSkillRepository extends JpaRepository<UserSkill, UUID> {

    @EntityGraph(attributePaths = {"skill", "skill.category"})
    List<UserSkill> findByUserId(UUID userId);

    @EntityGraph(attributePaths = {"skill", "skill.category"})
    List<UserSkill> findByUserIdAndType(UUID userId, SkillType type);

    @EntityGraph(attributePaths = {"skill", "skill.category", "user"})
    Optional<UserSkill> findById(UUID id);

    boolean existsByUserIdAndSkill_IdAndType(UUID userId, UUID skillId, SkillType type);

    List<UserSkill> findBySkill_IdInAndType(List<UUID> skillIds, SkillType type);

    @EntityGraph(attributePaths = {"skill", "skill.category", "user"})
    Optional<UserSkill> findByIdAndUserId(UUID id, UUID userId);
}
