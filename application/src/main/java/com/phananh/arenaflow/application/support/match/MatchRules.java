package com.phananh.arenaflow.application.support.match;

import java.time.Duration;

/** Hằng nghiệp vụ của kèo dùng chung (giờ bắt đầu tối thiểu, thời lượng tối đa). */
public final class MatchRules {

    /** Giờ bắt đầu phải cách hiện tại ít nhất khoảng này. */
    public static final Duration MIN_LEAD_TIME = Duration.ofHours(1);
    /** Thời lượng trận tối đa. */
    public static final Duration MAX_DURATION = Duration.ofHours(12);

    private MatchRules() {
    }
}
