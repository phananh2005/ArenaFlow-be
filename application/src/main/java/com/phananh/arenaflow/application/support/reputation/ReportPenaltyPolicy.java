package com.phananh.arenaflow.application.support.reputation;

import com.phananh.arenaflow.domain.entity.rating.Report;

/**
 * Bảng điểm trừ theo loại report và lần bị report loại đó (lần 1, 2, 3, từ 4 trở đi). Giá trị dương; SQL chọn phần tử
 * theo bộ đếm sau khi tăng, chặn ở phần tử thứ 4.
 */
public final class ReportPenaltyPolicy {

    private ReportPenaltyPolicy() {
    }

    public static int[] tiers(Report.ReportType type) {
        return switch (type) {
            case NOSHOW -> new int[] {5, 8, 12, 15};
            case BAD_PLAY -> new int[] {3, 5, 8, 10};
            case BAD_COMMUNICATION -> new int[] {2, 4, 6, 8};
            case SKILL_MISMATCH -> new int[] {1, 2, 3, 4};
        };
    }
}
