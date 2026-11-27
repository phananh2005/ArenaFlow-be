package com.phananh.arenaflow.application.dto.response;

import com.phananh.arenaflow.domain.entity.chat.Notification;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationResponse(UUID notificationId, Notification.Type type, String title, String body,
                                   Notification.ReferenceType referenceType, UUID referenceId, boolean isRead,
                                   LocalDateTime createdAt) {
}
