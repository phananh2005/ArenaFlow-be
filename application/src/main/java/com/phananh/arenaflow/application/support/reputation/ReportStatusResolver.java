package com.phananh.arenaflow.application.support.reputation;

import java.time.LocalDateTime;

/**
 * Trạng thái report hiển thị: report bùng {@code AUTO_ACCEPTED} hết hạn tranh chấp hiện {@code CONFIRMED} (không có job
 * ghi trạng thái này).
 */
public final class ReportStatusResolver {

    private static final String NOSHOW = "NOSHOW";
    private static final String AUTO_ACCEPTED = "AUTO_ACCEPTED";

    private ReportStatusResolver() {
    }

    public static String effectiveStatus(String status, String reportType, LocalDateTime createdAt, LocalDateTime now) {
        if (AUTO_ACCEPTED.equals(status) && NOSHOW.equals(reportType)
                && !now.isBefore(createdAt.plus(ReputationRules.DISPUTE_WINDOW))) {
            return "CONFIRMED";
        }
        return status;
    }

    /** Hạn tranh chấp; {@code null} khi không phải report bùng. */
    public static LocalDateTime disputeDeadline(String reportType, LocalDateTime createdAt) {
        return NOSHOW.equals(reportType) ? createdAt.plus(ReputationRules.DISPUTE_WINDOW) : null;
    }

    /** Hiện nút tranh chấp: bên nhận, report bùng, trạng thái DB {@code AUTO_ACCEPTED}, còn trong hạn. */
    public static boolean canDispute(boolean received, String status, String reportType, LocalDateTime createdAt,
                                     LocalDateTime now) {
        return received && NOSHOW.equals(reportType) && AUTO_ACCEPTED.equals(status)
                && now.isBefore(createdAt.plus(ReputationRules.DISPUTE_WINDOW));
    }
}
