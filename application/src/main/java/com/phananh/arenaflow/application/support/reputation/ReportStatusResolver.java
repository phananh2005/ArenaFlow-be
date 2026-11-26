package com.phananh.arenaflow.application.support.reputation;

import com.phananh.arenaflow.application.service.ReportDirection;
import com.phananh.arenaflow.domain.entity.rating.Report;

import java.time.LocalDateTime;

/**
 * Trạng thái report hiệu lực và quyền tranh chấp, tính khi đọc. Không job nào ghi CONFIRMED khi hết hạn tranh chấp:
 * không luồng nào ra quyết định dựa trên CONFIRMED đã lưu, hạn tranh chấp được kiểm lại trong câu cập nhật có điều kiện.
 */
public final class ReportStatusResolver {

    private ReportStatusResolver() {
    }

    /** Report bùng còn AUTO_ACCEPTED mà đã tới hạn tranh chấp → CONFIRMED; còn lại giữ trạng thái đã lưu. */
    public static Report.Status effectiveStatus(Report.Status stored, Report.ReportType type, LocalDateTime createdAt,
                                                LocalDateTime now) {
        boolean expired = type == Report.ReportType.NOSHOW && !now.isBefore(createdAt.plus(ReputationRules.DISPUTE_WINDOW));
        return stored == Report.Status.AUTO_ACCEPTED && expired ? Report.Status.CONFIRMED : stored;
    }

    /** Hạn tranh chấp; null với loại không phải bùng. */
    public static LocalDateTime disputeDeadline(Report.ReportType type, LocalDateTime createdAt) {
        return type == Report.ReportType.NOSHOW ? createdAt.plus(ReputationRules.DISPUTE_WINDOW) : null;
    }

    /** Chỉ đội bị report, report bùng, trạng thái đã lưu AUTO_ACCEPTED và trước hạn. */
    public static boolean canDispute(ReportDirection direction, Report.ReportType type, Report.Status stored,
                                     LocalDateTime createdAt, LocalDateTime now) {
        return direction == ReportDirection.RECEIVED && type == Report.ReportType.NOSHOW
                && stored == Report.Status.AUTO_ACCEPTED && now.isBefore(disputeDeadline(type, createdAt));
    }
}
