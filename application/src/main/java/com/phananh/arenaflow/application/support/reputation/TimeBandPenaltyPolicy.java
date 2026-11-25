package com.phananh.arenaflow.application.support.reputation;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Mức trừ điểm theo khoảng cách từ lúc thao tác tới giờ bắt đầu trận: ≥ 24 giờ, 4 giờ đến dưới 24 giờ, dưới 4 giờ.
 * Biên đúng 24 giờ / đúng 4 giờ thuộc khung nhẹ hơn.
 */
public record TimeBandPenaltyPolicy(int atLeast24h, int from4hTo24h, int under4h) {

    /** Huỷ kèo đã ghép / rút yêu cầu đã được chấp nhận. */
    public static final TimeBandPenaltyPolicy MATCH_CANCELLATION = new TimeBandPenaltyPolicy(-2, -5, -10);

    private static final Duration DAY = Duration.ofHours(24);
    private static final Duration FOUR_HOURS = Duration.ofHours(4);

    public int penaltyFor(LocalDateTime matchStart, LocalDateTime at) {
        Duration remaining = Duration.between(at, matchStart);
        if (remaining.compareTo(DAY) >= 0) {
            return atLeast24h;
        }
        return remaining.compareTo(FOUR_HOURS) >= 0 ? from4hTo24h : under4h;
    }
}
