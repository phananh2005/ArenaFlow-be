package com.phananh.arenaflow.application.support.venue;

import com.phananh.arenaflow.application.support.storage.CloudinaryProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Khung thời gian xác nhận upload và kích thước job dọn asset mồ côi; biểu thức cron do scheduler đọc. */
@ConfigurationProperties(prefix = "arenaflow.venue.image-cleanup")
public record VenueImageCleanupProperties(Duration grace, Duration confirmWindow, Duration clockSkew, Integer pageSize,
                                          Integer maxAdminCallsPerRun, Duration connectTimeout, Duration readTimeout) {

    private static final Duration SIGNATURE_LIFETIME = Duration.ofHours(1);

    public VenueImageCleanupProperties {
        grace = grace == null ? Duration.ofHours(24) : grace;
        confirmWindow = confirmWindow == null ? Duration.ofHours(6) : confirmWindow;
        clockSkew = clockSkew == null ? Duration.ofMinutes(5) : clockSkew;
        pageSize = pageSize == null ? 500 : pageSize;
        maxAdminCallsPerRun = maxAdminCallsPerRun == null ? 100 : maxAdminCallsPerRun;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(30) : readTimeout;
    }

    /**
     * Kiểm điều kiện trước khi cho bật job dọn dẹp: các môi trường dùng chung một cloud phải dùng folder riêng,
     * nếu không môi trường này sẽ xoá asset mà môi trường khác vẫn tham chiếu; thời gian ân hạn phải lớn hơn mọi
     * luồng xác nhận.
     */
    public void requireSafeToEnable(CloudinaryProperties cloudinary) {
        if (!cloudinary.isConfigured()) {
            throw new IllegalStateException("Venue image cleanup is enabled but Cloudinary is not configured");
        }
        if ("arenaflow".equals(cloudinary.folder().trim())) {
            throw new IllegalStateException("Venue image cleanup requires a dedicated per-environment Cloudinary folder");
        }
        if (confirmWindow.plus(SIGNATURE_LIFETIME).compareTo(grace) >= 0) {
            throw new IllegalStateException("Venue image cleanup grace must exceed confirm-window + 1h");
        }
    }
}
