package com.phananh.arenaflow.common.time;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/** Kiểm nguồn "bây giờ" theo giờ JVM và việc JVM chạy test luôn ở giờ Việt Nam. */
class AppTimeTest {

    @Test
    void jvmRunningTestsUsesVietnamTimeZone() {
        // Build ghim múi giờ cho JVM chạy test, không phụ thuộc múi giờ của máy hay cờ gõ trong lệnh.
        assertThat(ZoneId.systemDefault()).isEqualTo(ZoneId.of("Asia/Ho_Chi_Minh"));
    }

    @Test
    void nowIsCurrentTimeTruncatedToMicros() {
        LocalDateTime now = AppTime.now();

        // Độ chính xác bằng TIMESTAMP PostgreSQL: không có phần dưới micro giây.
        assertThat(now.getNano() % 1_000).isZero();
        assertThat(Duration.between(now, LocalDateTime.now()).abs()).isLessThan(Duration.ofSeconds(1));
    }

    @Test
    void todayIsCurrentCalendarDate() {
        LocalDate before = LocalDate.now();
        LocalDate today = AppTime.today();
        LocalDate after = LocalDate.now();

        // before/after phủ trường hợp chạy đúng lúc qua nửa đêm.
        assertThat(today).isIn(before, after);
    }
}
