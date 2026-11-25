package com.phananh.arenaflow.application.service;

/** Job vòng đời kèo: hết hạn (OPEN quá hạn) và lưu trữ (CONFIRMED qua giờ kết thúc). */
public interface MatchLifecycleJobService {

    /** Chuyển EXPIRED theo lô (yêu cầu PENDING → EXPIRED cùng lô); trả số kèo đã chuyển. */
    int expireDueMatches();

    /** Chuyển ARCHIVED theo lô; trả số kèo đã chuyển. */
    int archiveFinishedMatches();
}
