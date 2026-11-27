package com.gakbisa.backend.service;

import com.gakbisa.backend.config.AppExceptions.ForbiddenException;
import com.gakbisa.backend.config.AppExceptions.NotFoundException;
import com.gakbisa.backend.config.AppExceptions.ValidationException;
import com.gakbisa.backend.domain.*;
import com.gakbisa.backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class ChatService {

    public record ChatMessageDto(
        UUID id,
        UUID roomId,
        UUID senderId,
        String senderName,
        String messageType,
        String content,
        Instant createdAt,
        boolean isMe
    ) {}

    private final ChatRoomRepository chatRooms;
    private final ChatMemberRepository chatMembers;
    private final ChatMessageRepository chatMessages;
    private final LearningSessionRepository sessions;
    private final UserRepository users;
    private final NotificationService notifications;
    private final SecureRandom random = new SecureRandom();

    public ChatService(ChatRoomRepository chatRooms, ChatMemberRepository chatMembers,
                       ChatMessageRepository chatMessages, LearningSessionRepository sessions,
                       UserRepository users, NotificationService notifications) {
        this.chatRooms = chatRooms;
        this.chatMembers = chatMembers;
        this.chatMessages = chatMessages;
        this.sessions = sessions;
        this.users = users;
        this.notifications = notifications;
    }

    /**
     * Memastikan ruang chat pribadi antar-peserta sesi belajar sudah ada,
     * serta memvalidasi bahwa pengguna yang meminta adalah peserta sesi.
     */
    @Transactional
    public ChatRoom getOrCreateSessionRoom(UUID currentUserId, UUID sessionId) {
        LearningSession session = sessions.findById(sessionId)
            .orElseThrow(() -> new NotFoundException("Sesi belajar tidak ditemukan"));

        if (session.getBarterRequest() == null) {
            throw new ForbiddenException("Chat sesi barter tidak berlaku untuk sesi kelompok; buka chat kelompoknya");
        }

        User requester = session.getBarterRequest().getRequester();
        User target = session.getBarterRequest().getTargetUser();

        if (!currentUserId.equals(requester.getId()) && !currentUserId.equals(target.getId())) {
            throw new ForbiddenException("Kamu bukan peserta sesi belajar ini");
        }

        String directKey = "session:" + sessionId;

        ChatRoom room = chatRooms.findByDirectKey(directKey).orElseGet(() -> {
            ChatRoom cr = new ChatRoom();
            cr.setType(ChatRoomType.PRIVATE);
            cr.setDirectKey(directKey);
            cr.setCreatedBy(requester);
            cr.setCurrentKeyVersion(1);
            return chatRooms.save(cr);
        });

        // Pastikan kedua peserta tercatat di chat_members
        ensureMember(room, requester);
        ensureMember(room, target);

        return room;
    }

    private void ensureMember(ChatRoom room, User user) {
        chatMembers.findByRoom_IdAndUser_Id(room.getId(), user.getId()).orElseGet(() -> {
            ChatMember cm = new ChatMember();
            cm.setRoom(room);
            cm.setUser(user);
            cm.setRole(ChatMemberRole.MEMBER);
            return chatMembers.save(cm);
        });
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDto> listSessionMessages(UUID currentUserId, UUID sessionId) {
        ChatRoom room = getOrCreateSessionRoom(currentUserId, sessionId);
        List<ChatMessage> list = chatMessages.findActiveByRoomId(room.getId());

        return list.stream().map(m -> {
            String text = decodeText(m.getCiphertext());
            boolean isMe = m.getSender().getId().equals(currentUserId);
            return new ChatMessageDto(
                m.getId(),
                room.getId(),
                m.getSender().getId(),
                m.getSender().getFullName(),
                m.getMessageType().name(),
                text,
                m.getCreatedAt(),
                isMe
            );
        }).toList();
    }

    @Transactional
    public ChatMessageDto sendSessionMessage(UUID currentUserId, UUID sessionId, String text) {
        if (text == null || text.isBlank()) {
            throw new ValidationException("Pesan tidak boleh kosong");
        }
        if (text.length() > 2000) {
            throw new ValidationException("Pesan maksimal 2000 karakter");
        }

        LearningSession session = sessions.findById(sessionId)
            .orElseThrow(() -> new NotFoundException("Sesi belajar tidak ditemukan"));

        if (session.getBarterRequest() == null) {
            throw new ForbiddenException("Chat sesi barter tidak berlaku untuk sesi kelompok; buka chat kelompoknya");
        }

        User requester = session.getBarterRequest().getRequester();
        User target = session.getBarterRequest().getTargetUser();

        ChatRoom room = getOrCreateSessionRoom(currentUserId, sessionId);
        User sender = users.findById(currentUserId)
            .orElseThrow(() -> new NotFoundException("Pengguna tidak ditemukan"));

        // Enkripsi / encoding payload sesuai constraint PostgreSQL (ciphertext & iv not null)
        String ciphertext = Base64.getEncoder().encodeToString(text.trim().getBytes(StandardCharsets.UTF_8));
        byte[] ivBytes = new byte[12];
        random.nextBytes(ivBytes);
        String iv = Base64.getEncoder().encodeToString(ivBytes);

        ChatMessage msg = new ChatMessage();
        msg.setRoom(room);
        msg.setSender(sender);
        msg.setKeyVersion(room.getCurrentKeyVersion());
        msg.setMessageType(ChatMessageType.TEXT);
        msg.setCiphertext(ciphertext);
        msg.setIv(iv);

        ChatMessage saved = chatMessages.save(msg);

        // Kirim notifikasi pesan baru ke partner sesi
        User partner = currentUserId.equals(requester.getId()) ? target : requester;
        String preview = text.trim();
        if (preview.length() > 60) preview = preview.substring(0, 57) + "…";
        notifications.push(partner, "CHAT", "Pesan baru dari " + sender.getFullName(), preview);

        return new ChatMessageDto(
            saved.getId(),
            room.getId(),
            sender.getId(),
            sender.getFullName(),
            saved.getMessageType().name(),
            text.trim(),
            saved.getCreatedAt(),
            true
        );
    }

    private String decodeText(String ciphertext) {
        if (ciphertext == null) return "";
        try {
            byte[] bytes = Base64.getDecoder().decode(ciphertext);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return ciphertext;
        }
    }
}
