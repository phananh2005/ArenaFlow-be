package com.phananh.arenaflow.application.support.reputation;

import com.phananh.arenaflow.domain.entity.rating.Report;

import java.util.EnumMap;
import java.util.Map;

/**
 * Bảng trừ điểm khi bị report theo số lần bị report loại đó (kể cả lần này): phần tử i = lần thứ i + 1, phần tử cuối
 * áp cho lần 4 trở đi. Giá trị dương; câu SQL trừ và chặn 0.
 */
public final class ReportPenaltyPolicy {

    private static final Map<Report.ReportType, int[]> TIERS = new EnumMap<>(Map.of(
            Report.ReportType.NOSHOW, new int[]{5, 8, 12, 15},
            Report.ReportType.BAD_PLAY, new int[]{3, 5, 8, 10},
            Report.ReportType.BAD_COMMUNICATION, new int[]{2, 4, 6, 8},
            Report.ReportType.SKILL_MISMATCH, new int[]{1, 2, 3, 4}));

    private ReportPenaltyPolicy() {
    }

    /** Bản sao mảng 4 bậc của loại report. */
    public static int[] tiers(Report.ReportType type) {
        return TIERS.get(type).clone();
    }
}
