package com.phananh.arenaflow.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.task.ThreadPoolTaskSchedulerBuilder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulingConfigTest {

    @Test
    void jobSchedulerIsBuiltFromBootPropertiesBuilder() {
        ThreadPoolTaskScheduler scheduler = new SchedulingConfig()
                .taskScheduler(new ThreadPoolTaskSchedulerBuilder().poolSize(5).threadNamePrefix("scheduling-"));

        assertThat(scheduler.getPoolSize()).isEqualTo(5);
        assertThat(scheduler.getThreadNamePrefix()).isEqualTo("scheduling-");
    }
}
