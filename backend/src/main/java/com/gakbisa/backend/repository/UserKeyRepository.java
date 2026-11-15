package com.gakbisa.backend.repository;

import com.gakbisa.backend.domain.UserKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserKeyRepository extends JpaRepository<UserKey, UUID> {

    Optional<UserKey> findFirstByUser_IdAndActiveTrue(UUID userId);
}
