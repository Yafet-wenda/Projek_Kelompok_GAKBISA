package com.gakbisa.backend.domain;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(name = "chat_message_nonces", uniqueConstraints =
    @UniqueConstraint(name = "uq_chat_message_nonces_room_key_iv", columnNames = {"room_id", "key_version", "iv"}))
@Immutable
@Getter
public class ChatMessageNonce {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false, updatable = false)
    private ChatRoom room;

    @Column(name = "key_version", nullable = false, updatable = false)
    private int keyVersion;

    @Column(nullable = false, updatable = false, length = 16)
    private String iv;

    protected ChatMessageNonce() {}

    public ChatMessageNonce(ChatRoom room, int keyVersion, String iv) {
        this.room = room;
        this.keyVersion = keyVersion;
        this.iv = iv;
    }
}
