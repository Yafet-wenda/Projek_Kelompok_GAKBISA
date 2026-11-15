package com.gakbisa.backend.repository;

import com.gakbisa.backend.domain.ChatRoomKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ChatRoomKeyRepository extends JpaRepository<ChatRoomKey, UUID> {

    List<ChatRoomKey> findByRoom_IdAndUser_IdAndCreatedAtGreaterThanEqualOrderByKeyVersionAsc(
        UUID roomId, UUID userId, Instant joinedAt
    );
}
