package com.gakbisa.backend.controller;

import com.gakbisa.backend.dto.ChatDtos.*;
import com.gakbisa.backend.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chat;

    public ChatController(ChatService chat) {
        this.chat = chat;
    }

    @PostMapping("/keys")
    @ResponseStatus(HttpStatus.CREATED)
    public UserKeyView publishKey(@Valid @RequestBody UserKeyRequest request) {
        return chat.publishKey(CurrentUser.id(), request);
    }

    @GetMapping("/keys/me")
    public UserKeyView myKey() {
        return chat.myKey(CurrentUser.id());
    }

    @GetMapping("/keys/users/{userId}")
    public PublicKeyView publicKey(@PathVariable UUID userId) {
        return chat.publicKey(CurrentUser.id(), userId);
    }

    @PostMapping("/rooms/direct")
    @ResponseStatus(HttpStatus.CREATED)
    public RoomView createDirect(@Valid @RequestBody DirectRoomRequest request) {
        return chat.createDirectRoom(CurrentUser.id(), request);
    }

    @PostMapping("/rooms/group")
    @ResponseStatus(HttpStatus.CREATED)
    public RoomView createGroup(@Valid @RequestBody GroupRoomRequest request) {
        return chat.createGroupRoom(CurrentUser.id(), request);
    }

    @GetMapping("/rooms")
    public List<RoomSummary> listRooms() {
        return chat.listRooms(CurrentUser.id());
    }

    @GetMapping("/rooms/{roomId}")
    public RoomView room(@PathVariable UUID roomId) {
        return chat.getRoom(CurrentUser.id(), roomId);
    }

    @PutMapping("/rooms/{roomId}/keys")
    public RoomView rotateKeys(@PathVariable UUID roomId,
                               @Valid @RequestBody MembershipRequest request) {
        return chat.rotateRoomKey(CurrentUser.id(), roomId, request);
    }

    @PostMapping("/rooms/{roomId}/members")
    public RoomView addMember(@PathVariable UUID roomId, @RequestParam UUID userId,
                              @Valid @RequestBody MembershipRequest request) {
        return chat.addMember(CurrentUser.id(), roomId, userId, request);
    }

    @PostMapping("/rooms/{roomId}/members/{userId}/remove")
    public RoomView removeMember(@PathVariable UUID roomId, @PathVariable UUID userId,
                                 @Valid @RequestBody MembershipRequest request) {
        return chat.removeMember(CurrentUser.id(), roomId, userId, request);
    }

    @PostMapping("/rooms/{roomId}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@PathVariable UUID roomId,
                      @Valid @RequestBody MembershipRequest request) {
        chat.leaveRoom(CurrentUser.id(), roomId, request);
    }

    @GetMapping("/rooms/{roomId}/messages")
    public List<MessageView> history(@PathVariable UUID roomId,
                                     @RequestParam(required = false) Instant beforeAt,
                                     @RequestParam(required = false) UUID beforeId,
                                     @RequestParam(defaultValue = "50") int limit) {
        return chat.history(CurrentUser.id(), roomId, beforeAt, beforeId, limit);
    }

    @PostMapping("/rooms/{roomId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageView sendMessage(@PathVariable UUID roomId,
                                   @Valid @RequestBody MessageRequest request) {
        return chat.sendMessage(CurrentUser.id(), roomId, request);
    }

    @PutMapping("/rooms/{roomId}/messages/{messageId}")
    public MessageView editMessage(@PathVariable UUID roomId, @PathVariable UUID messageId,
                                   @Valid @RequestBody MessageEditRequest request) {
        return chat.editMessage(CurrentUser.id(), roomId, messageId, request);
    }

    @DeleteMapping("/rooms/{roomId}/messages/{messageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMessage(@PathVariable UUID roomId, @PathVariable UUID messageId) {
        chat.deleteMessage(CurrentUser.id(), roomId, messageId);
    }

    @PostMapping("/rooms/{roomId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable UUID roomId) {
        chat.markRead(CurrentUser.id(), roomId);
    }
}
