package com.gakbisa.backend.repository;

import com.gakbisa.backend.domain.BarterAgreement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BarterAgreementRepository extends JpaRepository<BarterAgreement, UUID> {
    Optional<BarterAgreement> findByBarterRequestId(UUID barterRequestId);
}
