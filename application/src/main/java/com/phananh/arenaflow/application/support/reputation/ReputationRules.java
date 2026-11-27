package com.phananh.arenaflow.application.support.reputation;

import java.time.Duration;

/** Hằng uy tín một nơi; truyền vào SQL bằng tham số, không viết literal trong câu. */
public final class ReputationRules {

    public static final int COMPLETION_POINTS = 2;
    public static final int STREAK_MILESTONE = 5;
    public static final int STREAK_BONUS_5 = 5;
    public static final int STREAK_BONUS_10 = 10;
    public static final int FIVE_STAR_BONUS = 1;
    public static final Duration DISPUTE_WINDOW = Duration.ofHours(24);
    /** Số trận được tính tối thiểu để hiện tỉ lệ hoàn thành (dưới mức này là "Mới tham gia"). */
    public static final int NEWCOMER_THRESHOLD = 5;
    public static final int BADGE_MIN_COMPLETED_EXCLUSIVE = 5;
    public static final double BADGE_MIN_AVG_EXCLUSIVE = 3.0;
    public static final int BADGE_MIN_SCORE = 70;
    public static final int LEVEL_EXCELLENT_MIN = 95;
    public static final int LEVEL_NORMAL_MIN = 70;
    public static final int LEVEL_WARNING_MIN = 50;
    /** Rút lui / huỷ trong khoảng này trước giờ bắt đầu thì trận được tính vào mẫu số tỉ lệ hoàn thành. */
    public static final int CANCEL_WINDOW_HOURS = 24;

    private ReputationRules() {
    }
}
