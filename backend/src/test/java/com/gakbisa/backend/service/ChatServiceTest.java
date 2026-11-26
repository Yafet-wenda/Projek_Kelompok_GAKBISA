package com.gakbisa.backend.service;

import com.gakbisa.backend.config.AppExceptions.ForbiddenException;
import com.gakbisa.backend.config.AppExceptions.ValidationException;
import com.gakbisa.backend.domain.*;
import com.gakbisa.backend.dto.ChatDtos.MessageRequest;
import com.gakbisa.backend.dto.ChatDtos.MessageEditRequest;
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
    private ChatMessageNonceRepository nonces;
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
        nonces = mock(ChatMessageNonceRepository.class);
        service = new ChatService(users, userKeys, rooms, members, roomKeys, messages, nonces);

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
        when(nonces.existsByRoom_IdAndKeyVersionAndIv(
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
        when(nonces.existsByRoom_IdAndKeyVersionAndIv(
            roomId, 1, "AAAAAAAAAAAAAAAA")).thenReturn(true);

        assertThrows(ValidationException.class, () -> service.sendMessage(actorId, roomId,
            new MessageRequest(1, ChatMessageType.TEXT, "AAAAAAAAAAAAAAAAAAAAAA==",
                "AAAAAAAAAAAAAAAA", null, null)));

        verify(messages, never()).save(any(ChatMessage.class));
    }

    @Test
    void acceptsEncryptedInlineFileAttachment() {
        when(nonces.existsByRoom_IdAndKeyVersionAndIv(
            roomId, 1, "AAAAAAAAAAAAAAAA")).thenReturn(false);
        when(messages.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var saved = service.sendMessage(actorId, roomId,
            new MessageRequest(1, ChatMessageType.FILE, "AAAAAAAAAAAAAAAAAAAAAA==",
                "AAAAAAAAAAAAAAAA", null, null));

        assertEquals(ChatMessageType.FILE, saved.messageType());
        assertNull(saved.attachmentPath());
        verify(messages).save(any(ChatMessage.class));
    }

    @Test
    void doesNotAcceptUnencryptedStoragePathsForAttachments() {
        assertThrows(ValidationException.class, () -> service.sendMessage(actorId, roomId,
            new MessageRequest(1, ChatMessageType.FILE, "AAAAAAAAAAAAAAAAAAAAAA==",
                "AAAAAAAAAAAAAAAA", "some-user-file", null)));

        verify(messages, never()).save(any(ChatMessage.class));
    }

    @Test
    void reservesNonceBeforeSavingCiphertext() {
        when(messages.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.sendMessage(actorId, roomId, new MessageRequest(1, ChatMessageType.TEXT,
            "AAAAAAAAAAAAAAAAAAAAAA==", "AAAAAAAAAAAAAAAA", null, null));

        var order = inOrder(rooms, nonces, messages);
        order.verify(rooms).findByIdForUpdate(roomId);
        order.verify(nonces).existsByRoom_IdAndKeyVersionAndIv(roomId, 1, "AAAAAAAAAAAAAAAA");
        order.verify(nonces).saveAndFlush(any(ChatMessageNonce.class));
        order.verify(messages).save(any(ChatMessage.class));
    }

    @Test
    void rejectsEditUsingReservedNonceWithoutChangingMessage() {
        ChatMessage message = editableMessage();
        when(nonces.existsByRoom_IdAndKeyVersionAndIv(roomId, 1, "AQEBAQEBAQEBAQEB")).thenReturn(true);

        assertThrows(ValidationException.class, () -> service.editMessage(actorId, roomId, message.getId(),
            new MessageEditRequest("AQEBAQEBAQEBAQEBAQEBAQ==", "AQEBAQEBAQEBAQEB")));

        assertEquals("AAAAAAAAAAAAAAAA", message.getIv());
        assertEquals("AAAAAAAAAAAAAAAAAAAAAA==", message.getCiphertext());
        verify(nonces, never()).saveAndFlush(any());
        verify(messages, never()).save(any());
    }

    @Test
    void cannotEditMessageFromPreviousKeyEpoch() {
        ChatMessage message = editableMessage();
        room.setCurrentKeyVersion(2);

        assertThrows(ValidationException.class, () -> service.editMessage(actorId, roomId, message.getId(),
            new MessageEditRequest("AQEBAQEBAQEBAQEBAQEBAQ==", "AQEBAQEBAQEBAQEB")));

        verifyNoInteractions(nonces);
        verify(messages, never()).save(any());
    }

    @Test
    void legacyRoomRequiresKeyRotationBeforeSendOrEdit() {
        ChatMessage message = editableMessage();
        room.setMinMessageKeyVersion(2);

        assertThrows(ValidationException.class, () -> service.sendMessage(actorId, roomId,
            new MessageRequest(1, ChatMessageType.TEXT, "AAAAAAAAAAAAAAAAAAAAAA==",
                "AQEBAQEBAQEBAQEB", null, null)));
        assertThrows(ValidationException.class, () -> service.editMessage(actorId, roomId, message.getId(),
            new MessageEditRequest("AQEBAQEBAQEBAQEBAQEBAQ==", "AQEBAQEBAQEBAQEB")));

        verifyNoInteractions(nonces);
    }

    @Test
    void rejectsEditByAnotherSender() {
        ChatMessage message = editableMessage();
        User other = new User();
        other.setId(UUID.randomUUID());
        message.setSender(other);

        assertThrows(ForbiddenException.class, () -> service.editMessage(actorId, roomId, message.getId(),
            new MessageEditRequest("AQEBAQEBAQEBAQEBAQEBAQ==", "AQEBAQEBAQEBAQEB")));

        verifyNoInteractions(nonces);
    }

    @Test
    void rejectsEditFromFormerMember() {
        membership.setLeftAt(java.time.Instant.now());

        assertThrows(ForbiddenException.class, () -> service.editMessage(actorId, roomId, UUID.randomUUID(),
            new MessageEditRequest("AQEBAQEBAQEBAQEBAQEBAQ==", "AQEBAQEBAQEBAQEB")));

        verifyNoInteractions(messages, nonces);
    }

    private ChatMessage editableMessage() {
        ChatMessage message = new ChatMessage();
        message.setId(UUID.randomUUID());
        message.setRoom(room);
        message.setSender(actor);
        message.setKeyVersion(1);
        message.setCiphertext("AAAAAAAAAAAAAAAAAAAAAA==");
        message.setIv("AAAAAAAAAAAAAAAA");
        when(messages.findByIdAndRoom_Id(message.getId(), roomId)).thenReturn(Optional.of(message));
        return message;
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
