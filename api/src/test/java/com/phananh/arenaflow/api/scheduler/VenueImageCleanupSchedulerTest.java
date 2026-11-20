package com.phananh.arenaflow.api.scheduler;

import com.phananh.arenaflow.application.service.VenueImageCleanupService;
import com.phananh.arenaflow.application.support.storage.CloudinaryProperties;
import com.phananh.arenaflow.application.support.venue.VenueImageCleanupProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.config.ScheduledTaskHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@SpringBootTest
class VenueImageCleanupSchedulerTest {

    private static final VenueImageCleanupProperties DEFAULTS =
            new VenueImageCleanupProperties(null, null, null, null, null, null, null);

    @Autowired private ScheduledTaskHolder scheduledTaskHolder;

    @Test
    void cleanupIsOffByDefaultSoNoTaskIsRegistered() {
        String method = VenueImageCleanupScheduler.class.getName() + ".cleanupOrphanAssets";
        assertThat(scheduledTaskHolder.getScheduledTasks()).noneMatch(task -> method.equals(task.getTask().toString()));
    }

    @Test
    void enabledCronFailsFastOnSharedFolderButDisabledCronSkipsTheCheck() {
        CloudinaryProperties sharedFolder = new CloudinaryProperties("cloud", "key", "secret", "arenaflow");
        VenueImageCleanupService service = mock(VenueImageCleanupService.class);

        assertThatThrownBy(() -> new VenueImageCleanupScheduler("0 30 3 * * *", sharedFolder, DEFAULTS, service))
                .isInstanceOf(IllegalStateException.class);
        assertThatCode(() -> new VenueImageCleanupScheduler("-", sharedFolder, DEFAULTS, service))
                .doesNotThrowAnyException();
    }
}
