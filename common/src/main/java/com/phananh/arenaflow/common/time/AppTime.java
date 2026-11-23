package com.phananh.arenaflow.common.time;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Nguồn "bây giờ" cho nơi không inject được {@link java.time.Clock}
 * (entity callback, record factory, exception handler, validator). Service dùng bean {@code Clock}.
 * Giờ theo timezone JVM — JVM luôn chạy {@code -Duser.timezone=Asia/Ho_Chi_Minh}.
 */
public final class AppTime {

    /** Lớp tiện ích tĩnh, không tạo instance. */
    private AppTime() {
    }

    /** Giờ hiện tại, cắt về micro giây (độ chính xác của cột TIMESTAMP). */
    public static LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }

    /** Ngày lịch hiện tại. */
    public static LocalDate today() {
        return LocalDate.now();
    }
}
