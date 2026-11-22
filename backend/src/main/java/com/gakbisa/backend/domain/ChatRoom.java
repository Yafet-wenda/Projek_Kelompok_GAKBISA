package com.gakbisa.backend.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_rooms")
@Getter
@Setter
public class ChatRoom {

    @Id
    @UuidGenerator
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private ChatRoomType type;

    /** Wajib untuk GROUP, null untuk PRIVATE. */
    @Column(name = "name", length = 120)
    private String name;

    @Column(name = "avatar_path", columnDefinition = "text")
    private String avatarPath;

    /** Hanya untuk PRIVATE; lihat {@link #directKeyOf(UUID, UUID)}. */
    @Column(name = "direct_key", unique = true, length = 73)
    private String directKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    /** Naik 1 setiap anggota grup keluar/dikeluarkan (key rotation). */
    @Column(name = "current_key_version", nullable = false)
    private int currentKeyVersion = 1;

    @Column(name = "min_message_key_version", nullable = false)
    private int minMessageKeyVersion = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Kunci pasangan yang urutannya tidak bergantung siapa yang memulai chat. */
    public static String directKeyOf(UUID a, UUID b) {
        String x = a.toString();
        String y = b.toString();
        return x.compareTo(y) < 0 ? x + ":" + y : y + ":" + x;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
