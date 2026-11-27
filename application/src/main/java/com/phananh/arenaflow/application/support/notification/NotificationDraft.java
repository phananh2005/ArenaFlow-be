package com.phananh.arenaflow.application.support.notification;

import com.phananh.arenaflow.domain.entity.chat.Notification;

import java.util.UUID;

/** Một thông báo sắp ghi; {@code recipientUserId} null bị bỏ khi ghi (đội không còn đội trưởng). */
public record NotificationDraft(UUID recipientUserId, Notification.Type type, String title, String body,
                                Notification.ReferenceType referenceType, UUID referenceId) {
}
