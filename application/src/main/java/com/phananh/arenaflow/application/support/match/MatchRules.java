package com.phananh.arenaflow.application.support.match;

import java.time.Duration;

/** Hằng nghiệp vụ của kèo — một nơi cho validator và service. */
public final class MatchRules {

    /** Giờ bắt đầu phải cách lúc tạo ít nhất 1 giờ. */
    public static final Duration MIN_LEAD_TIME = Duration.ofHours(1);
    /** Hạn nhận đối không sớm hơn giờ bắt đầu quá 7 ngày. */
    public static final Duration MAX_POSTING_WINDOW = Duration.ofDays(7);
    /** Trận dài tối đa 12 giờ. */
    public static final Duration MAX_DURATION = Duration.ofHours(12);
    public static final int MAX_CUSTOM_ITEMS = 10;
    public static final long MAX_AMOUNT = 1_000_000_000L;
    public static final int MAX_VENUE_OPTIONS = 5;
    public static final int MAX_TIME_OPTIONS = 5;

    private MatchRules() {
    }
}
