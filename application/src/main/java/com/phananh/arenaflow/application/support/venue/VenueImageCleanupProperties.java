package com.phananh.arenaflow.application.support.venue;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "arenaflow.venue.image-cleanup")
public record VenueImageCleanupProperties(Duration grace, Duration confirmWindow, Duration clockSkew) {

    public VenueImageCleanupProperties {
        grace = grace == null ? Duration.ofHours(24) : grace;
        confirmWindow = confirmWindow == null ? Duration.ofHours(6) : confirmWindow;
        clockSkew = clockSkew == null ? Duration.ofMinutes(5) : clockSkew;
    }
}
