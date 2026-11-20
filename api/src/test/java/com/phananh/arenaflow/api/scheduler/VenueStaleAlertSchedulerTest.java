package com.phananh.arenaflow.api.scheduler;

import com.phananh.arenaflow.application.support.venue.VenueAlertProperties;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VenueStaleAlertSchedulerTest {

    @Nested
    @SpringBootTest(properties = "arenaflow.venue.stale-alert.cron=0 0 3 * * *")
    class Enabled {

        @Autowired private ScheduledTaskHolder scheduledTaskHolder;
        @Autowired private VenueAlertProperties properties;

        @Test
        void registersDailyCronAndBindsJobSizing() {
            assertThat(staleAlertCronTasks(scheduledTaskHolder)).singleElement()
                    .extracting(CronTask::getExpression).isEqualTo("0 0 3 * * *");
            assertThat(properties).isEqualTo(new VenueAlertProperties(180, 500, 20));
        }
    }

    @Nested
    @SpringBootTest(properties = "arenaflow.venue.stale-alert.cron=-")
    class Disabled {

        @Autowired private ScheduledTaskHolder scheduledTaskHolder;

        @Test
        void dashCronRegistersNoTask() {
            assertThat(staleAlertCronTasks(scheduledTaskHolder)).isEmpty();
        }
    }

    private static List<CronTask> staleAlertCronTasks(ScheduledTaskHolder holder) {
        // Spring bọc runnable của method được lập lịch nên so khớp theo mô tả task (class khai báo + method).
        String method = VenueStaleAlertScheduler.class.getName() + ".alertStaleVenues";
        return holder.getScheduledTasks().stream()
                .map(ScheduledTask::getTask)
                .filter(CronTask.class::isInstance)
                .map(CronTask.class::cast)
                .filter(task -> method.equals(task.toString()))
                .toList();
    }
}
