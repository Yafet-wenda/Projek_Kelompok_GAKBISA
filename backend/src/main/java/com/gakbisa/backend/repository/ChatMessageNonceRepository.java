package com.gakbisa.backend.repository;

import com.gakbisa.backend.domain.ChatMessageNonce;
import org.springframework.data.repository.Repository;

import java.util.UUID;

public interface ChatMessageNonceRepository extends Repository<ChatMessageNonce, UUID> {

    boolean existsByRoom_IdAndKeyVersionAndIv(UUID roomId, int keyVersion, String iv);

    ChatMessageNonce saveAndFlush(ChatMessageNonce nonce);
}
