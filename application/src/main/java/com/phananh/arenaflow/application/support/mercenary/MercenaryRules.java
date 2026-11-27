package com.phananh.arenaflow.application.support.mercenary;

/** Hằng nghiệp vụ tuyển mercenary (một nơi). */
public final class MercenaryRules {

    /** Số người tối đa mỗi tin tuyển. */
    public static final int MAX_QUANTITY = 10;
    /** Số vị trí tối đa mỗi lần tạo tin. */
    public static final int MAX_POSITIONS = 4;
    public static final double DEFAULT_RADIUS_KM = 3;
    public static final double MIN_RADIUS_KM = 1;
    public static final double MAX_RADIUS_KM = 20;
    /** Khoảng cách trả ra được làm tròn lên bội số này để không suy ra vị trí chính xác của sân. */
    public static final double DISTANCE_ROUNDING_KM = 0.5;
    public static final int MAX_OPPONENT_NAME = 100;

    private MercenaryRules() {
    }
}
