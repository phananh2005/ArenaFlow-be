package com.phananh.arenaflow.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.task.ThreadPoolTaskSchedulerBuilder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/** Kiểm tra cấu hình scheduler của job định kỳ và chốt chặn xoá bảng chat_message trong V7. */
class SchedulingConfigTest {

    @Test
    void jobSchedulerIsBuiltFromBootPropertiesBuilder() {
        ThreadPoolTaskScheduler scheduler = new SchedulingConfig()
                .taskScheduler(new ThreadPoolTaskSchedulerBuilder().poolSize(5).threadNamePrefix("scheduling-"));

        assertThat(scheduler.getPoolSize()).isEqualTo(5);
        assertThat(scheduler.getThreadNamePrefix()).isEqualTo("scheduling-");
    }

    @Test
    void v7MigrationGuardsDropChatMessageWithNonEmptyCheck() throws IOException {
        String v7;
        try (InputStream stream = Objects.requireNonNull(
                getClass().getClassLoader().getResourceAsStream("db/migration/V7__match_flow_a_lifecycle.sql"))) {
            v7 = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        int guardIndex = v7.indexOf("IF EXISTS (SELECT 1 FROM chat_message");
        int dropIndex = v7.indexOf("DROP TABLE chat_message;");

        assertThat(guardIndex).as("chốt chặn kiểm tra chat_message khác rỗng trong V7").isNotNegative();
        assertThat(dropIndex).isGreaterThan(guardIndex);
    }
}
