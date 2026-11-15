package com.gakbisa.backend.dto;

import com.gakbisa.backend.domain.ChatMemberRole;
import com.gakbisa.backend.domain.ChatMessageType;
import com.gakbisa.backend.domain.ChatRoomType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ChatDtos {

    private ChatDtos() {}

    public record UserKeyRequest(
        @NotBlank @Size(max = 8192) String publicKey,
        @Size(max = 30) String algorithm,
        @Size(max = 16384) String encryptedPrivateKey,
        @Size(max = 512) String backupSalt,
        @Size(max = 512) String backupIv
    ) {}

    public record UserKeyView(UUID id, UUID userId, String publicKey, String algorithm,
                              String encryptedPrivateKey, String backupSalt, String backupIv,
                              Instant createdAt) {}

    public record PublicKeyView(UUID id, UUID userId, String publicKey, String algorithm) {}

    public record KeyEnvelopeRequest(
        @NotNull UUID userId,
        @NotNull UUID recipientKeyId,
        @NotBlank @Size(max = 4096) String ephemeralPublicKey,
        @NotBlank @Size(max = 256) String iv,
        @NotBlank @Size(max = 8192) String wrappedKey
    ) {}

    public record DirectRoomRequest(
        @NotNull UUID userId,
        @NotNull List<@Valid KeyEnvelopeRequest> keys
    ) {}

    public record GroupRoomRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull List<@NotNull UUID> memberIds,
        @NotNull List<@Valid KeyEnvelopeRequest> keys
    ) {}

    public record MembershipRequest(@NotNull List<@Valid KeyEnvelopeRequest> keys) {}

    public record MessageRequest(
        @Positive int keyVersion,
        @NotNull ChatMessageType messageType,
        @NotBlank @Size(max = 2_000_000) String ciphertext,
        @NotBlank @Size(max = 256) String iv,
        @Size(max = 2048) String attachmentPath,
        UUID replyToId
    ) {}

    public record MessageEditRequest(
        @NotBlank @Size(max = 2_000_000) String ciphertext,
        @NotBlank @Size(max = 256) String iv
    ) {}

    public record RoomSummary(
        UUID id, ChatRoomType type, String name, String avatarPath,
        int currentKeyVersion, Instant updatedAt, Instant lastMessageAt, long unreadCount
    ) {}

    public record MemberView(
        UUID userId, String fullName, String avatarPath, ChatMemberRole role,
        Instant joinedAt, Instant lastReadAt, PublicKeyView activeKey
    ) {}

    public record RoomKeyView(
        int keyVersion, UUID recipientKeyId, String ephemeralPublicKey,
        String iv, String wrappedKey, Instant createdAt
    ) {}

    public record RoomView(
        UUID id, ChatRoomType type, String name, String avatarPath,
        int currentKeyVersion, Instant createdAt, Instant updatedAt,
        List<MemberView> members, List<RoomKeyView> myKeys
    ) {}

    public record MessageView(
        UUID id, UUID roomId, UUID senderId, int keyVersion, ChatMessageType messageType,
        String ciphertext, String iv, String attachmentPath, UUID replyToId,
        Instant createdAt, Instant editedAt, Instant deletedAt
    ) {}
}
