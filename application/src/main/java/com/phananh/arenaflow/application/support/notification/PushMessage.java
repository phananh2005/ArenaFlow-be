package com.phananh.arenaflow.application.support.notification;

import com.phananh.arenaflow.domain.entity.chat.Notification;

import java.util.UUID;

public record PushMessage(Notification.Type type, String title, String body, Notification.ReferenceType referenceType,
                          UUID referenceId) {
}
