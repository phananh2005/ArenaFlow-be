package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.domain.entity.rating.Report;

import java.util.List;
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

    /**
     * Trận chuyển COMPLETED: một câu cho mọi entry (cộng điểm hoàn thành, chuỗi trận sạch, thưởng mốc, thưởng 5 sao).
     * Số đội cập nhật khác số entry → {@link IllegalStateException}.
     */
    void applyMatchCompletion(UUID matchId, List<CompletionEntry> entries);

    /** Trừ điểm theo bậc số lần bị report loại đó, đưa chuỗi về 0; trả số điểm thực trừ (≥ 0, có thể nhỏ hơn bảng). */
    int applyReportPenalty(UUID teamId, Report.ReportType type, UUID reportId);

    /** Hoàn đúng {@code points} đã thực trừ và giảm bộ đếm loại đó; không khôi phục chuỗi trận sạch. */
    void refundReportPenalty(UUID teamId, Report.ReportType type, int points, UUID reportId);
}
