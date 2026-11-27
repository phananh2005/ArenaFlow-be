package com.phananh.arenaflow.application.support.notification;

import java.time.Duration;

/** Hằng nghiệp vụ của thông báo — một nơi duy nhất. */
public final class NotificationRules {

    /** Thông báo hiển thị và được giữ 30 ngày kể từ lúc tạo. */
    public static final Duration RETENTION = Duration.ofDays(30);
    /** Cuộc trò chuyện được giữ 90 ngày kể từ khi trận kết thúc. */
    public static final Duration CHAT_RETENTION = Duration.ofDays(90);
    /** Nhắc trước giờ bắt đầu trận; ghép kèo khi còn ít hơn khoảng này thì không nhắc. */
    public static final Duration REMINDER_LEAD = Duration.ofHours(2);
    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 100;

    private NotificationRules() {
    }
}
