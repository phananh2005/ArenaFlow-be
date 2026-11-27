package com.phananh.arenaflow.api.chat;

import com.phananh.arenaflow.api.security.CurrentUser;
import com.phananh.arenaflow.application.dto.response.ConversationSummaryResponse;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.service.ConversationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/profile/conversations")
@RequiredArgsConstructor
@Validated
public class ProfileConversationController {

    private final ConversationService conversationService;

    @GetMapping
    public ResponseEntity<PageResponse<ConversationSummaryResponse>> listMyConversations(
            @CurrentUser UUID currentUserId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return ResponseEntity.ok(conversationService.listMyConversations(currentUserId, page, pageSize));
    }
}
