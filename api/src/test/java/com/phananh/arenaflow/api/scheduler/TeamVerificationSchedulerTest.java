package com.phananh.arenaflow.api.scheduler;

import com.phananh.arenaflow.application.support.reputation.TeamVerificationProperties;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TeamVerificationSchedulerTest {

    @Nested
    @SpringBootTest(properties = "arenaflow.reputation.verification.cron=0 0 3 * * *")
    class Enabled {

        @Autowired private ScheduledTaskHolder scheduledTaskHolder;
        @Autowired private TeamVerificationProperties properties;

        @Test
        void registersDailyCronAndBindsBatchSize() {
            assertThat(verificationCronTasks(scheduledTaskHolder)).singleElement()
                    .extracting(CronTask::getExpression).isEqualTo("0 0 3 * * *");
            assertThat(properties).isEqualTo(new TeamVerificationProperties(500));
        }
    }

    @Nested
    @SpringBootTest(properties = "arenaflow.reputation.verification.cron=-")
    class Disabled {

        @Autowired private ScheduledTaskHolder scheduledTaskHolder;

        @Test
        void dashCronRegistersNoTask() {
            assertThat(verificationCronTasks(scheduledTaskHolder)).isEmpty();
        }
    }

    private static List<CronTask> verificationCronTasks(ScheduledTaskHolder holder) {
        // Spring bọc runnable của method được lập lịch nên so khớp theo mô tả task (class khai báo + method).
        String method = TeamVerificationScheduler.class.getName() + ".reconcileVerificationBadges";
        return holder.getScheduledTasks().stream()
                .map(ScheduledTask::getTask)
                .filter(CronTask.class::isInstance)
                .map(CronTask.class::cast)
                .filter(task -> method.equals(task.toString()))
                .toList();
    }
}
