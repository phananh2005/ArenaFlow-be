package com.phananh.arenaflow.api.chat;

import com.phananh.arenaflow.api.security.CurrentUser;
import com.phananh.arenaflow.application.dto.request.ChatHistoryQuery;
import com.phananh.arenaflow.application.dto.request.SendConversationMessageRequest;
import com.phananh.arenaflow.application.dto.response.ApiResponse;
import com.phananh.arenaflow.application.dto.response.ChatHistoryResponse;
import com.phananh.arenaflow.application.dto.response.ConversationMessageResponse;
import com.phananh.arenaflow.application.dto.response.ConversationResponse;
import com.phananh.arenaflow.application.service.ConversationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/conversations/{conversationId}")
@RequiredArgsConstructor
@Validated
public class ConversationController {

    private final ConversationService conversationService;

    @GetMapping
    public ResponseEntity<ApiResponse<ConversationResponse>> getConversation(@PathVariable UUID conversationId,
                                                                             @CurrentUser UUID currentUserId) {
        return ResponseEntity.ok(ApiResponse.of(conversationService.getConversation(conversationId, currentUserId)));
    }

    @PostMapping("/messages")
    public ResponseEntity<ApiResponse<ConversationMessageResponse>> sendMessage(@PathVariable UUID conversationId,
            @CurrentUser UUID currentUserId, @Valid @RequestBody SendConversationMessageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(conversationService.send(conversationId, currentUserId, request)));
    }

    @GetMapping("/messages")
    public ResponseEntity<ApiResponse<ChatHistoryResponse>> history(@PathVariable UUID conversationId,
            @CurrentUser UUID currentUserId, @Valid @ModelAttribute ChatHistoryQuery query) {
        return ResponseEntity.ok(ApiResponse.of(conversationService.history(conversationId, currentUserId, query)));
    }

    @PostMapping("/messages/read")
    public ResponseEntity<Void> markRead(@PathVariable UUID conversationId, @CurrentUser UUID currentUserId) {
        conversationService.markRead(conversationId, currentUserId);
        return ResponseEntity.noContent().build();
    }
}
