package com.phananh.arenaflow.application.support.reputation;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Mức trừ điểm theo khoảng cách từ thời điểm thao tác tới giờ bắt đầu trận: từ đủ 24 giờ / từ đủ 4 giờ đến dưới 24 giờ /
 * dưới 4 giờ. Giá trị âm.
 */
public record TimeBandPenaltyPolicy(int atLeast24h, int from4hTo24h, int under4h) {

    public static final TimeBandPenaltyPolicy MATCH_CANCELLATION = new TimeBandPenaltyPolicy(-2, -5, -10);

    /** Mercenary rút lui sau khi đã được duyệt. */
    public static final TimeBandPenaltyPolicy MERCENARY_WITHDRAWAL = new TimeBandPenaltyPolicy(-1, -3, -5);

    private static final Duration DAY = Duration.ofHours(24);
    private static final Duration FOUR_HOURS = Duration.ofHours(4);

    public int penaltyFor(LocalDateTime matchStart, LocalDateTime at) {
        Duration lead = Duration.between(at, matchStart);
        if (lead.compareTo(DAY) >= 0) {
            return atLeast24h;
        }
        return lead.compareTo(FOUR_HOURS) >= 0 ? from4hTo24h : under4h;
    }
}
