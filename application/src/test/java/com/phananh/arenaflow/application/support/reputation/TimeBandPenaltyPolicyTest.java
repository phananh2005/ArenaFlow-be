package com.phananh.arenaflow.application.support.reputation;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TimeBandPenaltyPolicyTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 21, 18, 0);

    /** Bảng kiểm khung trừ điểm khi mercenary rút lui sau duyệt (số phút trước giờ bắt đầu → điểm bị trừ). */
    @ParameterizedTest
    @CsvSource({"1800, -1", "1440, -1", "1439, -3", "240, -3", "239, -5", "1, -5"})
    void mercenaryWithdrawalBands(long minutesBeforeStart, int expected) {
        assertThat(TimeBandPenaltyPolicy.MERCENARY_WITHDRAWAL.penaltyFor(START, START.minusMinutes(minutesBeforeStart)))
                .isEqualTo(expected);
    }
}
