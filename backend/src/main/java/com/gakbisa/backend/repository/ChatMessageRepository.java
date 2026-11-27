package com.gakbisa.backend.repository;

import com.gakbisa.backend.domain.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    @Query("select m from ChatMessage m join fetch m.sender where m.room.id = :roomId and m.deletedAt is null order by m.createdAt asc")
    List<ChatMessage> findActiveByRoomId(@Param("roomId") UUID roomId);
}
