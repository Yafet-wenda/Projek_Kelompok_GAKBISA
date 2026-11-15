package com.gakbisa.backend.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/** Pesan chat. {@code ciphertext} dan {@code iv} dibuat di client; server tidak pernah melihat plaintext. */
@Entity
@Table(name = "chat_messages")
@Getter
@Setter
public class ChatMessage {

    @Id
    @UuidGenerator
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private ChatRoom room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    /** Versi kunci ruang yang dipakai mengenkripsi pesan ini. */
    @Column(name = "key_version", nullable = false)
    private int keyVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 10)
    private ChatMessageType messageType = ChatMessageType.TEXT;

    /** Base64. Null setelah pesan dihapus untuk semua orang. */
    @Column(name = "ciphertext", columnDefinition = "text")
    private String ciphertext;

    /** Base64 nonce AES-GCM, harus unik per pesan. */
    @Column(name = "iv", columnDefinition = "text")
    private String iv;

    /** Path file terenkripsi di storage (kunci file ada di dalam payload terenkripsi). */
    @Column(name = "attachment_path", columnDefinition = "text")
    private String attachmentPath;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_to_id")
    private ChatMessage replyTo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /** Hapus untuk semua: buang isi, sisakan jejak baris. */
    public void markDeleted() {
        this.deletedAt = Instant.now();
        this.ciphertext = null;
        this.iv = null;
        this.attachmentPath = null;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
