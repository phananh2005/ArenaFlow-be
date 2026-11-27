package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.NotificationListQuery;
import com.phananh.arenaflow.application.dto.response.NotificationHistoryResponse;
import com.phananh.arenaflow.application.dto.response.UnreadCountResponse;

import java.util.UUID;

/**
 * Thông báo của chính người gọi trong 30 ngày gần nhất. Người gọi không tồn tại / đã xoá → {@code USER_001} ở mọi
 * method.
 */
public interface NotificationService {

    /** Mốc con trỏ không phải thông báo của người gọi trong 30 ngày → {@code NOTIFICATION_002}. 2 truy vấn. */
    NotificationHistoryResponse listMine(UUID callerId, NotificationListQuery query);

    /** 1 truy vấn. */
    UnreadCountResponse countUnread(UUID callerId);

    /** Không phải thông báo của người gọi → {@code NOTIFICATION_001}; đã đọc → không ghi (idempotent). */
    void markRead(UUID callerId, UUID notificationId);

    /** Đánh dấu mọi thông báo chưa đọc đang tồn tại lúc xử lý. */
    void markAllRead(UUID callerId);
}
