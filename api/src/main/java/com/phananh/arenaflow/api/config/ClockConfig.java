package com.phananh.arenaflow.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

/** Nguồn "bây giờ" cho service/job: theo timezone JVM, độ phân giải micro giây (bằng cột TIMESTAMP). */
@Configuration
public class ClockConfig {

    /** Đồng hồ theo múi giờ JVM, làm tròn xuống micro giây để giá trị ghi DB không bị cắt khác khi đọc lại. */
    @Bean
    public Clock clock() {
        return Clock.tick(Clock.systemDefaultZone(), Duration.ofNanos(1_000));
    }
}
