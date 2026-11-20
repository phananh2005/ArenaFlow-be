package com.phananh.arenaflow.api.scheduler;

import com.phananh.arenaflow.application.service.VenueImageCleanupService;
import com.phananh.arenaflow.application.support.storage.CloudinaryProperties;
import com.phananh.arenaflow.application.support.venue.VenueImageCleanupProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Chỉ kích hoạt job; mặc định tắt (cron "-"). Bật khi cấu hình storage chưa an toàn → khởi động thất bại. */
@Component
public class VenueImageCleanupScheduler {

    private final VenueImageCleanupService venueImageCleanupService;

    public VenueImageCleanupScheduler(@Value("${arenaflow.venue.image-cleanup.cron}") String cron,
                                      CloudinaryProperties cloudinaryProperties,
                                      VenueImageCleanupProperties cleanupProperties,
                                      VenueImageCleanupService venueImageCleanupService) {
        if (!"-".equals(cron.trim())) {
            cleanupProperties.requireSafeToEnable(cloudinaryProperties);
        }
        this.venueImageCleanupService = venueImageCleanupService;
    }

    @Scheduled(cron = "${arenaflow.venue.image-cleanup.cron}", zone = "Asia/Ho_Chi_Minh")
    public void cleanupOrphanAssets() {
        venueImageCleanupService.cleanupOrphanAssets();
    }
}
