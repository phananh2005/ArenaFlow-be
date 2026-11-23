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

    /** Cảnh báo Admin về địa điểm lâu không được chọn, theo cron cấu hình; cron chạy theo timezone JVM (giờ Việt Nam). */
    @Scheduled(cron = "${arenaflow.venue.stale-alert.cron}")
    public void alertStaleVenues() {
        venueAlertService.alertStaleVenues();
    }
}
