package com.gakbisa.backend.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_members",
       uniqueConstraints = @UniqueConstraint(columnNames = {"room_id", "user_id"}))
@Getter
@Setter
public class ChatMember {

    @Id
    @UuidGenerator
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private ChatRoom room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 10)
    private ChatMemberRole role = ChatMemberRole.MEMBER;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    /** null = masih anggota. Saat bergabung lagi, isi null kembali (jangan buat baris baru). */
    @Column(name = "left_at")
    private Instant leftAt;

    @Column(name = "last_read_at")
    private Instant lastReadAt;

    @Column(name = "muted", nullable = false)
    private boolean muted = false;

    public boolean isActiveMember() {
        return leftAt == null;
    }

    @PrePersist
    void onCreate() {
        if (joinedAt == null) joinedAt = Instant.now();
    }
}
