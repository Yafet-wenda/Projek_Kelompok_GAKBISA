package com.gakbisa.backend.controller;

import com.gakbisa.backend.service.ChatService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/sessions/{sessionId}/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping
    public List<ChatService.ChatMessageDto> listMessages(@PathVariable UUID sessionId) {
        return chatService.listSessionMessages(CurrentUser.id(), sessionId);
    }

    @PostMapping
    public ChatService.ChatMessageDto sendMessage(@PathVariable UUID sessionId, @RequestBody Map<String, String> body) {
        String message = body.get("message");
        return chatService.sendSessionMessage(CurrentUser.id(), sessionId, message);
    }
}
