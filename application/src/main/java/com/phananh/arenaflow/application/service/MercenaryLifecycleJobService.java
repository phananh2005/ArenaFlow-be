package com.phananh.arenaflow.application.service;

/** Tác vụ định kỳ của tuyển mercenary — gọi từ job vòng đời kèo sau bước lưu trữ kèo đã kết thúc. */
public interface MercenaryLifecycleJobService {

    /** Ghi CLOSED (trận huỷ) / EXPIRED (tới giờ kết thúc) cho tin còn mở, từ chối đơn chờ; trả số tin đã xử lý. */
    int closeDueRecruitments();

    /** Từ chối đơn chờ còn sót trên tin đã đóng / hết hạn; trả số đơn. */
    int rejectOrphanPending();

    /** Báo mercenary đã duyệt về trận bị huỷ (kể cả tin đã đóng trước đó); trả số đơn đã đánh dấu. */
    int notifyCancelledGames();
}
