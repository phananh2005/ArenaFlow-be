package com.phananh.arenaflow.api.config;

import org.springframework.boot.task.ThreadPoolTaskSchedulerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
public class SchedulingConfig {

    /**
     * Scheduler của mọi {@code @Scheduled}, dựng từ builder của Spring Boot nên theo {@code spring.task.scheduling.*}
     * (số luồng, tiền tố tên). Khai báo tường minh với tên {@code taskScheduler} vì kênh STOMP tạo thêm bean
     * TaskScheduler khác (broker, heartbeat): khi đó Boot không tự tạo scheduler cho job và {@code @Scheduled} chỉ
     * chọn được bean theo tên này.
     */
    @Bean(name = "taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler(ThreadPoolTaskSchedulerBuilder builder) {
        return builder.build();
    }
}
