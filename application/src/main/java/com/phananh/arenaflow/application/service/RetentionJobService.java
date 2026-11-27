package com.phananh.arenaflow.application.service;

/** Xoá dữ liệu hết hạn lưu (chạy hằng ngày). Mỗi tác vụ trả số dòng đã xoá; lô lỗi dừng tác vụ đó. */
public interface RetentionJobService {

    /** Cuộc trò chuyện (kèm tin nhắn) có trận kết thúc quá 90 ngày. */
    int purgeExpiredConversations();

    /** Thông báo tạo quá 30 ngày. */
    int purgeExpiredNotifications();

    /** Dấu "đã nhắc" ghi quá 30 ngày. */
    int purgeOldReminders();
}
