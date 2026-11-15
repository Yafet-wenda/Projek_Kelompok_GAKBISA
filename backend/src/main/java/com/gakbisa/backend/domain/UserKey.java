package com.gakbisa.backend.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/** Kunci publik user untuk E2EE chat. Kunci privat TIDAK pernah disimpan dalam bentuk plaintext. */
@Entity
@Table(name = "user_keys")
@Getter
@Setter
public class UserKey {

    @Id
    @UuidGenerator
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "public_key", nullable = false, columnDefinition = "text")
    private String publicKey;

    @Column(name = "algorithm", nullable = false, length = 30)
    private String algorithm = "ECDH-P256";

    /** Cadangan kunci privat, sudah dienkripsi di client (opsional). */
    @Column(name = "encrypted_private_key", columnDefinition = "text")
    private String encryptedPrivateKey;

    @Column(name = "backup_salt", columnDefinition = "text")
    private String backupSalt;

    @Column(name = "backup_iv", columnDefinition = "text")
    private String backupIv;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
