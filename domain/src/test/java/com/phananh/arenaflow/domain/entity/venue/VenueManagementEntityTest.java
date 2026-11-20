package com.phananh.arenaflow.domain.entity.venue;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class VenueManagementEntityTest {

    @Test
    void venueCarriesVersionAlertGatesWardAndTypedVenueType() {
        UUID wardId = UUID.randomUUID();
        OffsetDateTime alertTime = OffsetDateTime.parse("2026-11-18T00:00:00Z");

        Venue venue = Venue.builder()
                .venueType(Venue.VenueType.SEVEN_A_SIDE)
                .wardId(wardId)
                .staleAlertedAt(alertTime)
                .closureAlertedAt(alertTime)
                .build();

        assertThat(venue.getVenueType()).isEqualTo(Venue.VenueType.SEVEN_A_SIDE);
        assertThat(venue.getWardId()).isEqualTo(wardId);
        assertThat(venue.getStaleAlertedAt()).isEqualTo(alertTime);
        assertThat(venue.getClosureAlertedAt()).isEqualTo(alertTime);
        assertThat(venue.getVersion()).isNull();
    }

    @Test
    void venueImageCarriesCloudinaryPublicId() {
        VenueImage image = VenueImage.builder().publicId("arenaflow-dev/venues/test-image").build();

        assertThat(image.getPublicId()).isEqualTo("arenaflow-dev/venues/test-image");
    }

    @Test
    void venueReportDefaultsToPendingAndCarriesStructuredLifecycle() {
        UUID adminId = UUID.randomUUID();
        OffsetDateTime handledAt = OffsetDateTime.parse("2026-11-18T00:00:00Z");

        VenueReport report = VenueReport.builder()
                .reason(VenueReport.Reason.CLOSED)
                .detail("Venue closed")
                .status(VenueReport.Status.VERIFIED)
                .handledBy(adminId)
                .handledAt(handledAt)
                .build();
        VenueReport pending = VenueReport.builder().reason(VenueReport.Reason.OTHER).build();

        assertThat(report.getReason()).isEqualTo(VenueReport.Reason.CLOSED);
        assertThat(report.getDetail()).isEqualTo("Venue closed");
        assertThat(report.getStatus()).isEqualTo(VenueReport.Status.VERIFIED);
        assertThat(report.getHandledBy()).isEqualTo(adminId);
        assertThat(report.getHandledAt()).isEqualTo(handledAt);
        assertThat(pending.getStatus()).isEqualTo(VenueReport.Status.PENDING);
    }
}
