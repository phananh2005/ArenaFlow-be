package com.phananh.arenaflow.application.dto.request;

import com.phananh.arenaflow.application.support.notification.NotificationRules;
import com.phananh.arenaflow.application.validation.ValidNotificationCursor;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.UUID;

/** Tham số danh sách thông báo (phân trang con trỏ theo {@code notificationId} mốc). */
@ValidNotificationCursor
public record NotificationListQuery(
        Boolean unreadOnly,
        UUID before,
        UUID after,
        @Min(1) @Max(NotificationRules.MAX_LIMIT) Integer limit) {

    public NotificationListQuery {
        unreadOnly = unreadOnly != null && unreadOnly;
        limit = limit == null ? NotificationRules.DEFAULT_LIMIT : limit;
    }
}
