package com.gakbisa.backend.repository;

import com.gakbisa.backend.domain.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    @Query("""
        select m from ChatMessage m
        where m.room.id = :roomId
          and (:beforeAt is null or m.createdAt < :beforeAt
               or (m.createdAt = :beforeAt and m.id < :beforeId))
        order by m.createdAt desc, m.id desc
        """)
    List<ChatMessage> findHistory(@Param("roomId") UUID roomId,
                                  @Param("beforeAt") Instant beforeAt,
                                  @Param("beforeId") UUID beforeId,
                                  Pageable pageable);

    Optional<ChatMessage> findByIdAndRoom_Id(UUID id, UUID roomId);

    Optional<ChatMessage> findFirstByRoom_IdOrderByCreatedAtDescIdDesc(UUID roomId);

    @Query("""
        select count(m) from ChatMessage m
        where m.room.id = :roomId
          and m.sender.id <> :userId
          and m.deletedAt is null
          and (:lastReadAt is null or m.createdAt > :lastReadAt)
        """)
    long countUnread(@Param("roomId") UUID roomId, @Param("userId") UUID userId,
                     @Param("lastReadAt") Instant lastReadAt);
}
