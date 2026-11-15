package com.gakbisa.backend.service;

import com.gakbisa.backend.config.AppExceptions.ForbiddenException;
import com.gakbisa.backend.config.AppExceptions.ValidationException;
import com.gakbisa.backend.domain.*;
import com.gakbisa.backend.dto.ChatDtos.MessageRequest;
import com.gakbisa.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ChatServiceTest {

    private UserRepository users;
    private ChatRoomRepository rooms;
    private ChatMemberRepository members;
    private ChatMessageRepository messages;
    private ChatService service;

    private final UUID actorId = UUID.randomUUID();
    private final UUID roomId = UUID.randomUUID();
    private User actor;
    private ChatRoom room;
    private ChatMember membership;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        UserKeyRepository userKeys = mock(UserKeyRepository.class);
        rooms = mock(ChatRoomRepository.class);
        members = mock(ChatMemberRepository.class);
        ChatRoomKeyRepository roomKeys = mock(ChatRoomKeyRepository.class);
        messages = mock(ChatMessageRepository.class);
        service = new ChatService(users, userKeys, rooms, members, roomKeys, messages);

        actor = new User();
        actor.setId(actorId);
        room = new ChatRoom();
        room.setId(roomId);
        room.setCurrentKeyVersion(1);
        room.setType(ChatRoomType.PRIVATE);
        membership = new ChatMember();
        membership.setRoom(room);
        membership.setUser(actor);
        membership.setRole(ChatMemberRole.MEMBER);

        when(users.findById(actorId)).thenReturn(Optional.of(actor));
        when(rooms.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(members.findByRoom_IdAndUser_Id(roomId, actorId)).thenReturn(Optional.of(membership));
    }

    @Test
    void sendsEncryptedMessageForCurrentRoomKey() {
        when(messages.existsByRoom_IdAndKeyVersionAndIv(
            roomId, 1, "AAAAAAAAAAAAAAAA")).thenReturn(false);
        when(messages.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var saved = service.sendMessage(actorId, roomId,
            new MessageRequest(1, ChatMessageType.TEXT, "AAAAAAAAAAAAAAAAAAAAAA==",
                "AAAAAAAAAAAAAAAA", null, null));

        assertEquals(roomId, saved.roomId());
        assertEquals(actorId, saved.senderId());
        assertEquals("AAAAAAAAAAAAAAAAAAAAAA==", saved.ciphertext());
        verify(messages).save(any(ChatMessage.class));
    }

    @Test
    void rejectsMessageFromNonMember() {
        membership.setLeftAt(java.time.Instant.now());

        assertThrows(ForbiddenException.class, () -> service.sendMessage(actorId, roomId,
            new MessageRequest(1, ChatMessageType.TEXT, "AAAAAAAAAAAAAAAAAAAAAA==",
                "AAAAAAAAAAAAAAAA", null, null)));

        verify(messages, never()).save(any(ChatMessage.class));
    }

    @Test
    void rejectsStaleKeyVersion() {
        room.setCurrentKeyVersion(2);

        assertThrows(ValidationException.class, () -> service.sendMessage(actorId, roomId,
            new MessageRequest(1, ChatMessageType.TEXT, "AAAAAAAAAAAAAAAAAAAAAA==",
                "AAAAAAAAAAAAAAAA", null, null)));

        verify(messages, never()).save(any(ChatMessage.class));
    }

    @Test
    void rejectsReusedMessageIv() {
        when(messages.existsByRoom_IdAndKeyVersionAndIv(
            roomId, 1, "AAAAAAAAAAAAAAAA")).thenReturn(true);

        assertThrows(ValidationException.class, () -> service.sendMessage(actorId, roomId,
            new MessageRequest(1, ChatMessageType.TEXT, "AAAAAAAAAAAAAAAAAAAAAA==",
                "AAAAAAAAAAAAAAAA", null, null)));

        verify(messages, never()).save(any(ChatMessage.class));
    }

    @Test
    void doesNotAcceptAttachmentsWithoutAnUploadFlow() {
        assertThrows(ValidationException.class, () -> service.sendMessage(actorId, roomId,
            new MessageRequest(1, ChatMessageType.FILE, "AAAAAAAAAAAAAAAAAAAAAA==",
                "AAAAAAAAAAAAAAAA", "some-user-file", null)));

        verify(messages, never()).save(any(ChatMessage.class));
    }

    @Test
    void deletedMessageKeepsNonceReserved() {
        ChatMessage message = new ChatMessage();
        message.setCiphertext("encrypted");
        message.setIv("AAAAAAAAAAAAAAAA");

        message.markDeleted();

        assertNull(message.getCiphertext());
        assertEquals("AAAAAAAAAAAAAAAA", message.getIv());
        assertTrue(message.isDeleted());
    }
}
