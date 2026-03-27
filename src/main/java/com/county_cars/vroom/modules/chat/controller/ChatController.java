package com.county_cars.vroom.modules.chat.controller;

import com.county_cars.vroom.modules.chat.dto.ChatResponse;
import com.county_cars.vroom.modules.chat.dto.MessageResponse;
import com.county_cars.vroom.modules.chat.dto.OpenChatRequest;
import com.county_cars.vroom.modules.chat.service.ChatService;
import com.county_cars.vroom.modules.keycloak.CurrentUserService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/chats")
@RequiredArgsConstructor
@Hidden
@Tag(name = "Chat", description = "REST APIs for chat management and message history")
public class ChatController {

    private final ChatService        chatService;
    private final CurrentUserService currentUserService;

    @PostMapping
    @Operation(summary = "Open or retrieve a chat with another user")
    public ResponseEntity<ChatResponse> openChat(@Valid @RequestBody OpenChatRequest request) {
        Long currentUserId = currentUserService.getCurrentUserProfileId();
        return ResponseEntity.status(HttpStatus.OK)
                .body(chatService.openOrGetChat(currentUserId,
                        request.getOtherUserId(), request.getListingId()));
    }

    @GetMapping
    @Operation(summary = "List all chats for the current user, newest first")
    public ResponseEntity<List<ChatResponse>> listChats() {
        Long currentUserId = currentUserService.getCurrentUserProfileId();
        return ResponseEntity.ok(chatService.listChats(currentUserId));
    }

    @GetMapping("/{chatId}/messages")
    @Operation(summary = "Paginated message history for a chat (newest first)")
    public ResponseEntity<Page<MessageResponse>> getMessages(
            @PathVariable Long chatId,
            @PageableDefault(size = 30, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        Long currentUserId = currentUserService.getCurrentUserProfileId();
        return ResponseEntity.ok(chatService.getMessages(chatId, currentUserId, pageable));
    }
}

