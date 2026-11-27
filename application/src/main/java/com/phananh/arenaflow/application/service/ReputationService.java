package com.phananh.arenaflow.application.service;

import java.util.UUID;

/**
 * Lõi ghi điểm uy tín dùng chung: một câu UPDATE nguyên tử chặn [0, 100], phải chạy trong transaction của thao tác
 * nghiệp vụ (không qua event).
 */
public interface ReputationService {

    /**
     * Bắt buộc có transaction của caller; delta 0 và không cắt chuỗi → không truy vấn. Đội không tồn tại →
     * IllegalStateException (lỗi lập trình).
     */
    void adjustTeamReliability(UUID teamId, ReliabilityChange change);

    /** Như {@link #adjustTeamReliability} cho điểm cá nhân. */
    void adjustUserReliability(UUID userId, ReliabilityChange change);
}
