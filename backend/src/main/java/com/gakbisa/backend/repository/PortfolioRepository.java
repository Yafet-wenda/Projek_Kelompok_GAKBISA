package com.gakbisa.backend.repository;

import com.gakbisa.backend.domain.Portfolio;
import com.gakbisa.backend.domain.PortfolioVisibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PortfolioRepository extends JpaRepository<Portfolio, UUID> {

    @EntityGraph(attributePaths = "user")
    List<Portfolio> findByUserId(UUID userId);

    Page<Portfolio> findByVisibility(PortfolioVisibility visibility, Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Optional<Portfolio> findById(UUID id);

    long countByUserId(UUID userId);
}
