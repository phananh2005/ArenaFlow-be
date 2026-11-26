package com.phananh.arenaflow.application.support.reputation;

import java.time.Duration;

/**
 * Hằng nghiệp vụ uy tín ở một nơi. Hằng dùng trong SQL được truyền bằng tham số, không viết literal trong câu.
 */
public final class ReputationRules {

    /** Điểm cộng cho mỗi đội khi trận chuyển COMPLETED. */
    public static final int COMPLETION_POINTS = 2;
    /** Chuỗi trận sạch chạm bội số của giá trị này → thưởng, đưa mọi bộ đếm report về 0. */
    public static final int STREAK_MILESTONE = 5;
    public static final int STREAK_BONUS_5 = 5;
    /** Thưởng khi chuỗi chạm bội số của 2 × mốc (10, 20, …), thay cho thưởng mốc 5. */
    public static final int STREAK_BONUS_10 = 10;
    public static final int FIVE_STAR_BONUS = 1;
    /** Đội bị report bùng có thời hạn này kể từ lúc report để tranh chấp. */
    public static final Duration DISPUTE_WINDOW = Duration.ofHours(24);
    /** Đội tự huỷ kèo đã ghép dưới số giờ này trước giờ bắt đầu → trận vào mẫu số tỉ lệ hoàn thành của đội đó. */
    public static final int LATE_CANCEL_WINDOW_HOURS = 24;
    /** Số trận được tính dưới ngưỡng này → đội "mới tham gia", chưa có tỉ lệ hoàn thành. */
    public static final int NEWCOMER_THRESHOLD = 5;
    /** Huy hiệu: số trận hoàn thành phải lớn hơn (không bằng) giá trị này. */
    public static final int BADGE_MIN_COMPLETED_EXCLUSIVE = 5;
    /** Huy hiệu: sao trung bình phải lớn hơn (không bằng) giá trị này. */
    public static final double BADGE_MIN_AVG_EXCLUSIVE = 3.0;
    /** Huy hiệu: điểm uy tín tối thiểu (bao gồm). */
    public static final int BADGE_MIN_SCORE = 70;

    private static final int EXCELLENT_FROM = 95;
    private static final int NORMAL_FROM = 70;
    private static final int WARNING_FROM = 50;

    private ReputationRules() {
    }

    /** Mức hiển thị của điểm uy tín — chỉ để hiển thị, không hạn chế chức năng. */
    public static ReliabilityLevel reliabilityLevel(int score) {
        if (score >= EXCELLENT_FROM) {
            return ReliabilityLevel.EXCELLENT;
        }
        if (score >= NORMAL_FROM) {
            return ReliabilityLevel.NORMAL;
        }
        return score >= WARNING_FROM ? ReliabilityLevel.WARNING : ReliabilityLevel.CRITICAL;
    }

    public enum ReliabilityLevel {
        EXCELLENT, NORMAL, WARNING, CRITICAL
    }
}
