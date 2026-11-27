package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.domain.entity.rating.Report;

import java.util.UUID;

/**
 * Lõi ghi điểm uy tín (đội / cá nhân) — mọi method chạy trong transaction của thao tác gốc ({@code MANDATORY}) bằng câu
 * UPDATE nguyên tử; phát {@code ReliabilityScoreChangedEvent} khi điểm đổi.
 */
public interface ReputationService {

    /** Cộng/trừ điểm cá nhân; {@code delta = 0} và không cắt chuỗi → không truy vấn. */
    void adjustUserReliability(UUID userId, ReliabilityChange change);

    /** Report nhắm vào đội: tăng bộ đếm, cắt chuỗi, trừ điểm theo bậc. Trả điểm thực trừ (≥ 0). */
    int applyReportPenalty(UUID teamId, Report.ReportType type, UUID reportId);

    /** Hoàn điểm cho đội khi report bị bác. */
    void refundReportPenalty(UUID teamId, Report.ReportType type, int points, UUID reportId);

    /**
     * Mercenary hoàn thành trận (đội trưởng chấm sao): +điểm hoàn thành, chuỗi trận sạch, thưởng mốc, +thưởng 5 sao —
     * một câu.
     */
    void applyMercenaryCompletion(UUID userId, UUID matchId, UUID externalMatchId, int ratingBonus, UUID ratingId);

    /** Report nhắm vào cá nhân (mercenary). Trả điểm thực trừ (≥ 0). */
    int applyUserReportPenalty(UUID userId, Report.ReportType type, UUID reportId);

    /** Hoàn điểm cho cá nhân khi report bị bác. */
    void refundUserReportPenalty(UUID userId, Report.ReportType type, int points, UUID reportId);
}
