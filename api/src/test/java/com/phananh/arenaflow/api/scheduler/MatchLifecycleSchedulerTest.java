package com.phananh.arenaflow.api.scheduler;

import com.phananh.arenaflow.application.service.MatchLifecycleJobService;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchLifecycleSchedulerTest {

    private final MatchLifecycleJobService jobService = mock(MatchLifecycleJobService.class);
    private final MatchLifecycleScheduler scheduler = new MatchLifecycleScheduler(jobService);

    @Test
    void archiveStillRunsWhenExpiryFails() {
        when(jobService.expireDueMatches()).thenThrow(new IllegalStateException("boom"));

        scheduler.runLifecycle();

        verify(jobService).archiveFinishedMatches();
    }

    @Test
    void cronUsesConfigurablePropertyWithoutExplicitZone() throws NoSuchMethodException {
        Scheduled scheduled = MatchLifecycleScheduler.class.getMethod("runLifecycle").getAnnotation(Scheduled.class);

        assertThat(scheduled.cron()).isEqualTo("${arenaflow.match.lifecycle.cron}");
        assertThat(scheduled.zone()).isEmpty();
    }
}
