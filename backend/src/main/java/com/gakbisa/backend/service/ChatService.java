package com.gakbisa.backend.service;

import com.gakbisa.backend.config.AppExceptions.ForbiddenException;
import com.gakbisa.backend.config.AppExceptions.NotFoundException;
import com.gakbisa.backend.config.AppExceptions.ValidationException;
import com.gakbisa.backend.domain.*;
import com.gakbisa.backend.dto.ChatDtos.*;
import com.gakbisa.backend.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class ChatService {

    private static final String CHAT_ALGORITHM = "ECDH-P256";
    private static final int MAX_GROUP_MEMBERS = 100;
    private static final int MAX_HISTORY_PAGE_SIZE = 100;

    private final UserRepository users;
    private final UserKeyRepository userKeys;
    private final ChatRoomRepository rooms;
    private final ChatMemberRepository members;
    private final ChatRoomKeyRepository roomKeys;
    private final ChatMessageRepository messages;
    private final ChatMessageNonceRepository nonces;

    public ChatService(UserRepository users, UserKeyRepository userKeys, ChatRoomRepository rooms,
                       ChatMemberRepository members, ChatRoomKeyRepository roomKeys,
                       ChatMessageRepository messages, ChatMessageNonceRepository nonces) {
        this.users = users;
        this.userKeys = userKeys;
        this.rooms = rooms;
        this.members = members;
        this.roomKeys = roomKeys;
        this.messages = messages;
        this.nonces = nonces;
    }

    @Transactional
    public UserKeyView publishKey(UUID actorId, UserKeyRequest request) {
        User user = requireActiveUser(actorId);
        if (request.algorithm() != null && !CHAT_ALGORITHM.equals(request.algorithm())) {
            throw new ValidationException("Algoritma kunci yang didukung adalah " + CHAT_ALGORITHM);
        }
        boolean hasBackup = request.encryptedPrivateKey() != null
            || request.backupSalt() != null || request.backupIv() != null;
        if (hasBackup && (blank(request.encryptedPrivateKey()) || blank(request.backupSalt())
            || blank(request.backupIv()))) {
            throw new ValidationException("Cadangan kunci harus menyertakan encryptedPrivateKey, backupSalt, dan backupIv");
        }

        userKeys.findFirstByUser_IdAndActiveTrue(actorId).ifPresent(oldKey -> {
            oldKey.setActive(false);
            oldKey.setRevokedAt(Instant.now());
            userKeys.saveAndFlush(oldKey);
        });

        UserKey key = new UserKey();
        key.setUser(user);
        key.setPublicKey(request.publicKey().trim());
        key.setAlgorithm(CHAT_ALGORITHM);
        key.setEncryptedPrivateKey(request.encryptedPrivateKey());
        key.setBackupSalt(request.backupSalt());
        key.setBackupIv(request.backupIv());
        key.setActive(true);
        return toUserKeyView(userKeys.save(key));
    }

    @Transactional(readOnly = true)
    public UserKeyView myKey(UUID actorId) {
        requireActiveUser(actorId);
        return toUserKeyView(activeKey(actorId));
    }

    @Transactional(readOnly = true)
    public PublicKeyView publicKey(UUID actorId, UUID targetUserId) {
        requireActiveUser(actorId);
        requireActiveUser(targetUserId);
        UserKey key = activeKey(targetUserId);
        return new PublicKeyView(key.getId(), targetUserId, key.getPublicKey(), key.getAlgorithm());
    }

    @Transactional
    public RoomView createDirectRoom(UUID actorId, DirectRoomRequest request) {
        User creator = requireActiveUser(actorId);
        User other = requireActiveUser(request.userId());
        if (actorId.equals(other.getId())) {
            throw new ValidationException("Chat pribadi membutuhkan pengguna lain");
        }

        String directKey = ChatRoom.directKeyOf(actorId, other.getId());
        Optional<ChatRoom> existing = rooms.findByDirectKey(directKey);
        if (existing.isPresent()) {
            requireMembership(existing.get().getId(), actorId);
            return roomView(existing.get(), actorId);
        }

        ChatRoom room = new ChatRoom();
        room.setType(ChatRoomType.PRIVATE);
        room.setDirectKey(directKey);
        room.setCreatedBy(creator);
        room = rooms.saveAndFlush(room);

        ChatMember first = newMember(room, creator, ChatMemberRole.OWNER);
        ChatMember second = newMember(room, other, ChatMemberRole.MEMBER);
        List<ChatMember> savedMembers = members.saveAll(List.of(first, second));
        storeRoomKeys(room, 1, List.of(creator, other), request.keys());
        return roomView(room, actorId, savedMembers);
    }

    @Transactional
    public RoomView createGroupRoom(UUID actorId, GroupRoomRequest request) {
        User creator = requireActiveUser(actorId);
        List<UUID> requestedIds = request.memberIds();
        if (requestedIds.isEmpty() || requestedIds.size() >= MAX_GROUP_MEMBERS) {
            throw new ValidationException("Grup harus memiliki 1 sampai " + (MAX_GROUP_MEMBERS - 1) + " anggota selain pembuat");
        }
        Set<UUID> uniqueIds = new LinkedHashSet<>(requestedIds);
        if (uniqueIds.size() != requestedIds.size() || uniqueIds.contains(actorId)) {
            throw new ValidationException("Daftar anggota grup tidak valid");
        }

        List<User> participants = new ArrayList<>();
        participants.add(creator);
        for (UUID memberId : uniqueIds) {
            participants.add(requireActiveUser(memberId));
        }

        ChatRoom room = new ChatRoom();
        room.setType(ChatRoomType.GROUP);
        room.setName(request.name().trim());
        room.setCreatedBy(creator);
        room = rooms.saveAndFlush(room);

        List<ChatMember> groupMembers = new ArrayList<>();
        groupMembers.add(newMember(room, creator, ChatMemberRole.OWNER));
        for (int i = 1; i < participants.size(); i++) {
            groupMembers.add(newMember(room, participants.get(i), ChatMemberRole.MEMBER));
        }
        List<ChatMember> savedMembers = members.saveAll(groupMembers);
        storeRoomKeys(room, 1, participants, request.keys());
        return roomView(room, actorId, savedMembers);
    }

    @Transactional(readOnly = true)
    public List<RoomSummary> listRooms(UUID actorId) {
        requireActiveUser(actorId);
        List<ChatMember> memberships = members.findByUser_IdAndLeftAtIsNullOrderByRoom_UpdatedAtDesc(actorId);
        return memberships.stream().map(membership -> {
            ChatRoom room = membership.getRoom();
            Optional<ChatMessage> latest = messages.findFirstByRoom_IdOrderByCreatedAtDescIdDesc(room.getId());
            long unread = messages.countUnread(room.getId(), actorId, membership.getLastReadAt());
            return new RoomSummary(
                room.getId(), room.getType(), room.getName(), room.getAvatarPath(),
                room.getCurrentKeyVersion(), room.getUpdatedAt(),
                latest.map(ChatMessage::getCreatedAt).orElse(null), unread
            );
        }).toList();
    }

    @Transactional(readOnly = true)
    public RoomView getRoom(UUID actorId, UUID roomId) {
        User actor = requireActiveUser(actorId);
        ChatRoom room = requireRoom(roomId);
        List<ChatMember> activeMembers = activeMembers(roomId);
        requireActiveMembership(activeMembers, actorId);
        return roomView(room, actor.getId(), activeMembers);
    }

    @Transactional
    public RoomView rotateRoomKey(UUID actorId, UUID roomId, MembershipRequest request) {
        requireActiveUser(actorId);
        ChatRoom room = lockRoom(roomId);
        requireMembership(roomId, actorId);
        rotateKeys(room, activeMembers(roomId), request.keys());
        return roomView(room, actorId);
    }

    @Transactional
    public RoomView addMember(UUID actorId, UUID roomId, UUID targetUserId, MembershipRequest request) {
        requireActiveUser(actorId);
        ChatRoom room = lockRoom(roomId);
        ChatMember actorMembership = requireMembership(roomId, actorId);
        requireGroupManager(room, actorMembership);
        if (actorId.equals(targetUserId)) {
            throw new ValidationException("Kamu sudah menjadi anggota grup");
        }

        User target = requireActiveUser(targetUserId);
        Optional<ChatMember> previous = members.findByRoom_IdAndUser_Id(roomId, targetUserId);
        if (previous.isPresent() && previous.get().isActiveMember()) {
            throw new ValidationException("Pengguna sudah menjadi anggota grup");
        }
        ChatMember added = previous.orElseGet(() -> newMember(room, target, ChatMemberRole.MEMBER));
        added.setUser(target);
        added.setRoom(room);
        added.setRole(ChatMemberRole.MEMBER);
        added.setLeftAt(null);
        added.setJoinedAt(Instant.now());
        members.save(added);

        rotateKeys(room, activeMembers(roomId), request.keys());
        return roomView(room, actorId);
    }

    @Transactional
    public RoomView removeMember(UUID actorId, UUID roomId, UUID targetUserId, MembershipRequest request) {
        requireActiveUser(actorId);
        ChatRoom room = lockRoom(roomId);
        ChatMember actorMembership = requireMembership(roomId, actorId);
        requireGroupManager(room, actorMembership);
        if (actorId.equals(targetUserId)) {
            throw new ValidationException("Gunakan endpoint keluar untuk menghapus diri sendiri");
        }

        ChatMember target = requireMembership(roomId, targetUserId);
        if (target.getRole() == ChatMemberRole.OWNER
            || (actorMembership.getRole() == ChatMemberRole.ADMIN && target.getRole() == ChatMemberRole.ADMIN)) {
            throw new ForbiddenException("Kamu tidak dapat mengeluarkan pengelola grup ini");
        }
        target.setLeftAt(Instant.now());
        members.save(target);
        rotateKeys(room, activeMembers(roomId), request.keys());
        return roomView(room, actorId);
    }

    @Transactional
    public void leaveRoom(UUID actorId, UUID roomId, MembershipRequest request) {
        requireActiveUser(actorId);
        ChatRoom room = lockRoom(roomId);
        if (room.getType() != ChatRoomType.GROUP) {
            throw new ValidationException("Keanggotaan chat pribadi tidak dapat ditinggalkan");
        }
        ChatMember actorMembership = requireMembership(roomId, actorId);
        actorMembership.setLeftAt(Instant.now());
        members.save(actorMembership);

        List<ChatMember> remaining = activeMembers(roomId);
        if (remaining.isEmpty()) {
            throw new ValidationException("Anggota terakhir tidak dapat meninggalkan grup");
        }
        if (actorMembership.getRole() == ChatMemberRole.OWNER) {
            remaining.getFirst().setRole(ChatMemberRole.OWNER);
            members.save(remaining.getFirst());
        }
        rotateKeys(room, remaining, request.keys());
    }

    @Transactional
    public MessageView sendMessage(UUID actorId, UUID roomId, MessageRequest request) {
        User sender = requireActiveUser(actorId);
        ChatRoom room = lockRoom(roomId);
        requireMembership(roomId, actorId);
        validateMessagePayload(request.messageType(), request.ciphertext(), request.iv(), request.attachmentPath());
        requireWritableKey(room, request.keyVersion());
        String iv = canonicalIv(request.iv());

        ChatMessage message = new ChatMessage();
        message.setRoom(room);
        message.setSender(sender);
        message.setKeyVersion(request.keyVersion());
        message.setMessageType(request.messageType());
        message.setCiphertext(request.ciphertext());
        message.setIv(iv);
        message.setAttachmentPath(request.attachmentPath());
        if (request.replyToId() != null) {
            ChatMessage reply = messages.findByIdAndRoom_Id(request.replyToId(), roomId)
                .orElseThrow(() -> new NotFoundException("Pesan balasan tidak ditemukan"));
            message.setReplyTo(reply);
        }
        reserveNonce(room, request.keyVersion(), iv);
        room.setUpdatedAt(Instant.now());
        return toMessageView(messages.save(message));
    }

    @Transactional(readOnly = true)
    public List<MessageView> history(UUID actorId, UUID roomId, Instant beforeAt, UUID beforeId, int limit) {
        requireActiveUser(actorId);
        requireMembership(roomId, actorId);
        if ((beforeAt == null) != (beforeId == null)) {
            throw new ValidationException("beforeAt dan beforeId harus dikirim bersamaan");
        }
        if (limit < 1 || limit > MAX_HISTORY_PAGE_SIZE) {
            throw new ValidationException("Batas riwayat pesan harus antara 1 dan " + MAX_HISTORY_PAGE_SIZE);
        }
        return messages.findHistory(roomId, beforeAt, beforeId, PageRequest.of(0, limit))
            .stream().map(ChatService::toMessageView).toList();
    }

    @Transactional
    public void markRead(UUID actorId, UUID roomId) {
        requireActiveUser(actorId);
        ChatMember member = requireMembership(roomId, actorId);
        member.setLastReadAt(Instant.now());
        members.save(member);
    }

    @Transactional
    public MessageView editMessage(UUID actorId, UUID roomId, UUID messageId, MessageEditRequest request) {
        requireActiveUser(actorId);
        ChatRoom room = lockRoom(roomId);
        requireMembership(roomId, actorId);
        ChatMessage message = messages.findByIdAndRoom_Id(messageId, roomId)
            .orElseThrow(() -> new NotFoundException("Pesan tidak ditemukan"));
        if (!message.getSender().getId().equals(actorId)) {
            throw new ForbiddenException("Hanya pengirim yang dapat mengubah pesan");
        }
        if (message.isDeleted()) {
            throw new ValidationException("Pesan yang dihapus tidak dapat diubah");
        }
        requireWritableKey(room, message.getKeyVersion());
        validateMessagePayload(message.getMessageType(), request.ciphertext(), request.iv(), message.getAttachmentPath());
        String iv = canonicalIv(request.iv());
        reserveNonce(room, message.getKeyVersion(), iv);
        message.setCiphertext(request.ciphertext());
        message.setIv(iv);
        message.setEditedAt(Instant.now());
        return toMessageView(messages.save(message));
    }

    @Transactional
    public void deleteMessage(UUID actorId, UUID roomId, UUID messageId) {
        requireActiveUser(actorId);
        lockRoom(roomId);
        requireMembership(roomId, actorId);
        ChatMessage message = messages.findByIdAndRoom_Id(messageId, roomId)
            .orElseThrow(() -> new NotFoundException("Pesan tidak ditemukan"));
        if (!message.getSender().getId().equals(actorId)) {
            throw new ForbiddenException("Hanya pengirim yang dapat menghapus pesan");
        }
        if (!message.isDeleted()) {
            message.markDeleted();
            messages.save(message);
        }
    }

    private void requireWritableKey(ChatRoom room, int keyVersion) {
        if (keyVersion != room.getCurrentKeyVersion()) {
            throw new ValidationException("Pesan harus memakai versi kunci ruang yang terbaru");
        }
        if (keyVersion < room.getMinMessageKeyVersion()) {
            throw new ValidationException("Ganti kunci ruang sebelum mengirim atau mengubah pesan");
        }
    }

    private void reserveNonce(ChatRoom room, int keyVersion, String iv) {
        if (nonces.existsByRoom_IdAndKeyVersionAndIv(room.getId(), keyVersion, iv)) {
            throw new ValidationException("IV sudah pernah digunakan untuk versi kunci ini");
        }
        nonces.saveAndFlush(new ChatMessageNonce(room, keyVersion, iv));
    }

    private void rotateKeys(ChatRoom room, List<ChatMember> activeMembers,
                            List<KeyEnvelopeRequest> envelopes) {
        if (room.getCurrentKeyVersion() == Integer.MAX_VALUE) {
            throw new ValidationException("Versi kunci ruang sudah mencapai batas");
        }
        int nextVersion = room.getCurrentKeyVersion() + 1;
        List<User> recipients = activeMembers.stream().map(ChatMember::getUser).toList();
        storeRoomKeys(room, nextVersion, recipients, envelopes);
        room.setCurrentKeyVersion(nextVersion);
        room.setUpdatedAt(Instant.now());
        rooms.save(room);
    }

    private void storeRoomKeys(ChatRoom room, int keyVersion, Collection<User> recipients,
                               List<KeyEnvelopeRequest> envelopes) {
        if (envelopes == null || envelopes.size() != recipients.size()) {
            throw new ValidationException("Harus ada tepat satu kunci ruang terbungkus untuk setiap anggota aktif");
        }
        Map<UUID, User> expected = new HashMap<>();
        for (User user : recipients) {
            expected.put(user.getId(), user);
        }
        Set<UUID> seen = new HashSet<>();
        List<ChatRoomKey> toSave = new ArrayList<>();
        for (KeyEnvelopeRequest envelope : envelopes) {
            User recipient = expected.get(envelope.userId());
            if (recipient == null || !seen.add(envelope.userId())) {
                throw new ValidationException("Daftar penerima kunci ruang tidak sesuai dengan anggota aktif");
            }
            UserKey recipientKey = userKeys.findById(envelope.recipientKeyId())
                .orElseThrow(() -> new NotFoundException("Kunci publik penerima tidak ditemukan"));
            if (!recipientKey.isActive() || !recipientKey.getUser().getId().equals(recipient.getId())) {
                throw new ValidationException("Kunci penerima bukan kunci publik aktif anggota tersebut");
            }
            String iv = canonicalIv(envelope.iv());

            ChatRoomKey key = new ChatRoomKey();
            key.setRoom(room);
            key.setUser(recipient);
            key.setKeyVersion(keyVersion);
            key.setRecipientKey(recipientKey);
            key.setEphemeralPublicKey(envelope.ephemeralPublicKey());
            key.setIv(iv);
            key.setWrappedKey(envelope.wrappedKey());
            toSave.add(key);
        }
        if (seen.size() != expected.size()) {
            throw new ValidationException("Kunci ruang tidak mencakup semua anggota aktif");
        }
        roomKeys.saveAll(toSave);
    }

    private RoomView roomView(ChatRoom room, UUID actorId) {
        return roomView(room, actorId, activeMembers(room.getId()));
    }

    private RoomView roomView(ChatRoom room, UUID actorId, List<ChatMember> activeMembers) {
        List<MemberView> memberViews = activeMembers.stream().map(member -> {
            User user = member.getUser();
            Optional<UserKey> active = userKeys.findFirstByUser_IdAndActiveTrue(user.getId());
            PublicKeyView keyView = active.map(k -> new PublicKeyView(
                k.getId(), user.getId(), k.getPublicKey(), k.getAlgorithm()
            )).orElse(null);
            return new MemberView(user.getId(), user.getFullName(), user.getAvatarPath(),
                member.getRole(), member.getJoinedAt(), member.getLastReadAt(), keyView);
        }).toList();

        ChatMember self = activeMembers.stream()
            .filter(member -> member.getUser().getId().equals(actorId))
            .findFirst()
            .orElse(null);
        List<RoomKeyView> ownKeys = self == null ? List.of()
            : roomKeys.findByRoom_IdAndUser_IdAndCreatedAtGreaterThanEqualOrderByKeyVersionAsc(
                    room.getId(), actorId, self.getJoinedAt())
                .stream()
                .map(key -> new RoomKeyView(key.getKeyVersion(), key.getRecipientKey().getId(),
                    key.getEphemeralPublicKey(), key.getIv(), key.getWrappedKey(), key.getCreatedAt()))
                .toList();

        return new RoomView(room.getId(), room.getType(), room.getName(), room.getAvatarPath(),
            room.getCurrentKeyVersion(), room.getCreatedAt(), room.getUpdatedAt(), memberViews, ownKeys);
    }

    private User requireActiveUser(UUID id) {
        User user = users.findById(id).orElseThrow(() -> new NotFoundException("Pengguna tidak ditemukan"));
        if (user.getStatus() != AccountStatus.ACTIVE) {
            throw new ForbiddenException("Akun tidak aktif");
        }
        return user;
    }

    private UserKey activeKey(UUID userId) {
        return userKeys.findFirstByUser_IdAndActiveTrue(userId)
            .orElseThrow(() -> new NotFoundException("Pengguna belum memiliki kunci chat aktif"));
    }

    private ChatRoom requireRoom(UUID roomId) {
        return rooms.findById(roomId).orElseThrow(() -> new NotFoundException("Ruang chat tidak ditemukan"));
    }

    private ChatRoom lockRoom(UUID roomId) {
        return rooms.findByIdForUpdate(roomId).orElseThrow(() -> new NotFoundException("Ruang chat tidak ditemukan"));
    }

    private ChatMember requireMembership(UUID roomId, UUID userId) {
        ChatMember member = members.findByRoom_IdAndUser_Id(roomId, userId)
            .orElseThrow(() -> new ForbiddenException("Hanya anggota ruang yang dapat mengakses chat"));
        if (!member.isActiveMember()) {
            throw new ForbiddenException("Keanggotaan ruang chat sudah berakhir");
        }
        return member;
    }

    private void requireActiveMembership(List<ChatMember> activeMembers, UUID userId) {
        if (activeMembers.stream().noneMatch(member -> member.getUser().getId().equals(userId))) {
            throw new ForbiddenException("Hanya anggota aktif yang dapat mengakses chat");
        }
    }

    private List<ChatMember> activeMembers(UUID roomId) {
        return members.findByRoom_IdAndLeftAtIsNullOrderByJoinedAtAsc(roomId);
    }

    private void requireGroupManager(ChatRoom room, ChatMember actor) {
        if (room.getType() != ChatRoomType.GROUP) {
            throw new ValidationException("Operasi anggota hanya tersedia untuk ruang grup");
        }
        if (actor.getRole() != ChatMemberRole.OWNER && actor.getRole() != ChatMemberRole.ADMIN) {
            throw new ForbiddenException("Hanya pengelola grup yang dapat mengubah anggota");
        }
    }

    private static ChatMember newMember(ChatRoom room, User user, ChatMemberRole role) {
        ChatMember member = new ChatMember();
        member.setRoom(room);
        member.setUser(user);
        member.setRole(role);
        return member;
    }

    private static UserKeyView toUserKeyView(UserKey key) {
        return new UserKeyView(key.getId(), key.getUser().getId(), key.getPublicKey(), key.getAlgorithm(),
            key.getEncryptedPrivateKey(), key.getBackupSalt(), key.getBackupIv(), key.getCreatedAt());
    }

    private static MessageView toMessageView(ChatMessage message) {
        return new MessageView(message.getId(), message.getRoom().getId(), message.getSender().getId(),
            message.getKeyVersion(), message.getMessageType(), message.getCiphertext(),
            message.isDeleted() ? null : message.getIv(),
            message.getAttachmentPath(),
            message.getReplyTo() == null ? null : message.getReplyTo().getId(),
            message.getCreatedAt(), message.getEditedAt(), message.getDeletedAt());
    }

    private static void validateMessagePayload(ChatMessageType type, String ciphertext, String iv,
                                               String attachmentPath) {
        if (blank(ciphertext) || ciphertext.length() > 2_000_000) {
            throw new ValidationException("Ciphertext pesan kosong atau terlalu besar");
        }
        if (type != ChatMessageType.TEXT) {
            throw new ValidationException("Pesan lampiran belum didukung; kirim ciphertext teks terlebih dahulu");
        }
        if (validateBase64(ciphertext, "Ciphertext").length < 16) {
            throw new ValidationException("Ciphertext AES-GCM harus menyertakan authentication tag");
        }
        canonicalIv(iv);
        if (!blank(attachmentPath)) {
            throw new ValidationException("Pesan teks tidak boleh menyertakan path lampiran");
        }
    }

    private static String canonicalIv(String iv) {
        if (blank(iv)) {
            throw new ValidationException("IV AES-GCM harus berupa Base64 dari 12 byte");
        }
        byte[] decoded = validateBase64(iv, "IV");
        if (decoded.length != 12) {
            throw new ValidationException("IV AES-GCM harus berupa Base64 dari 12 byte");
        }
        return Base64.getEncoder().encodeToString(decoded);
    }

    private static byte[] validateBase64(String value, String field) {
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            throw new ValidationException(field + " harus menggunakan format Base64");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
