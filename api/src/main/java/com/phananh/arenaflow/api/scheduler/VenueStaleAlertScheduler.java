package com.phananh.arenaflow.api.scheduler;

import com.phananh.arenaflow.application.service.VenueAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Chỉ kích hoạt job; cron "-" là tắt job. */
@Component
@RequiredArgsConstructor
public class VenueStaleAlertScheduler {

    private final VenueAlertService venueAlertService;

    @Scheduled(cron = "${arenaflow.venue.stale-alert.cron}", zone = "Asia/Ho_Chi_Minh")
    public void alertStaleVenues() {
        venueAlertService.alertStaleVenues();
    }
}
