package com.gakbisa.backend.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Kunci simetris ruang (AES-256-GCM) yang sudah dibungkus untuk satu anggota.
 * Server tidak mampu membukanya karena butuh kunci privat si anggota.
 */
@Entity
@Table(name = "chat_room_keys",
       uniqueConstraints = @UniqueConstraint(columnNames = {"room_id", "user_id", "key_version"}))
@Getter
@Setter
public class ChatRoomKey {

    @Id
    @UuidGenerator
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private ChatRoom room;

    /** Penerima kunci. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "key_version", nullable = false)
    private int keyVersion;

    /** Kunci publik penerima yang dipakai saat membungkus. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_key_id", nullable = false)
    private UserKey recipientKey;

    @Column(name = "ephemeral_public_key", nullable = false, columnDefinition = "text")
    private String ephemeralPublicKey;

    @Column(name = "iv", nullable = false, columnDefinition = "text")
    private String iv;

    @Column(name = "wrapped_key", nullable = false, columnDefinition = "text")
    private String wrappedKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
