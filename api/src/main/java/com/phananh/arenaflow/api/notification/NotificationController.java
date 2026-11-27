package com.phananh.arenaflow.api.notification;

import com.phananh.arenaflow.api.security.CurrentUser;
import com.phananh.arenaflow.application.dto.request.NotificationListQuery;
import com.phananh.arenaflow.application.dto.response.ApiResponse;
import com.phananh.arenaflow.application.dto.response.NotificationHistoryResponse;
import com.phananh.arenaflow.application.dto.response.UnreadCountResponse;
import com.phananh.arenaflow.application.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/profile/notifications")
@RequiredArgsConstructor
@Validated
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<NotificationHistoryResponse>> listNotifications(
            @CurrentUser UUID currentUserId, @Valid @ModelAttribute NotificationListQuery query) {
        return ResponseEntity.ok(ApiResponse.of(notificationService.listMine(currentUserId, query)));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> countUnread(@CurrentUser UUID currentUserId) {
        return ResponseEntity.ok(ApiResponse.of(notificationService.countUnread(currentUserId)));
    }

    @PostMapping("/{notificationId}/read")
    public ResponseEntity<Void> markRead(@CurrentUser UUID currentUserId, @PathVariable UUID notificationId) {
        notificationService.markRead(currentUserId, notificationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read")
    public ResponseEntity<Void> markAllRead(@CurrentUser UUID currentUserId) {
        notificationService.markAllRead(currentUserId);
        return ResponseEntity.noContent().build();
    }
}
