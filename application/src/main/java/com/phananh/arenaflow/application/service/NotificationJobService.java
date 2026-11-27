package com.phananh.arenaflow.application.service;

/** Thông báo không có event nguồn — chạy trong lần chạy 5 phút của job vòng đời kèo. */
public interface NotificationJobService {

    /**
     * Nhắc một lần khoảng 2 giờ trước giờ bắt đầu: đội trưởng hai đội của kèo đã ghép + mercenary đã duyệt (kèo và
     * trận ngoài). Mỗi lô một transaction (claim dấu nhắc + đọc người nhận + ghi thông báo); lô lỗi rollback cả dấu
     * lẫn thông báo, dừng lượt chạy. Trả số trận đã nhắc.
     */
    int sendDueStartReminders();

    /** Nhắc mercenary đã duyệt đánh giá đội thuê sau khi trận ngoài hệ thống kết thúc (trong 24 giờ). Trả số trận. */
    int sendDueRatingReminders();
}
