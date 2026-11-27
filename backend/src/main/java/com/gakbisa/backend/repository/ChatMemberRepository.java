package com.gakbisa.backend.repository;

import com.gakbisa.backend.domain.ChatMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatMemberRepository extends JpaRepository<ChatMember, UUID> {
    Optional<ChatMember> findByRoom_IdAndUser_Id(UUID roomId, UUID userId);
    List<ChatMember> findByRoom_Id(UUID roomId);
    boolean existsByRoom_IdAndUser_IdAndLeftAtIsNull(UUID roomId, UUID userId);
}
