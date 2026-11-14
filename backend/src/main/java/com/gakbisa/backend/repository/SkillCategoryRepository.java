package com.gakbisa.backend.repository;

import com.gakbisa.backend.domain.SkillCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SkillCategoryRepository extends JpaRepository<SkillCategory, UUID> {

    Optional<SkillCategory> findByNameIgnoreCase(String name);
}
