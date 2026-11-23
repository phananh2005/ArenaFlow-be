package com.phananh.arenaflow.application.mapper;

import com.phananh.arenaflow.application.dto.response.AdminVenueResponse;
import com.phananh.arenaflow.application.dto.response.VenueImageResponse;
import com.phananh.arenaflow.application.dto.response.VenueResponse;
import com.phananh.arenaflow.application.dto.response.VenueSummaryResponse;
import com.phananh.arenaflow.domain.entity.venue.VenueImage;
import com.phananh.arenaflow.domain.projection.VenueDetailView;
import com.phananh.arenaflow.domain.projection.VenueSummaryView;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Mapper địa điểm: che tên người tạo cho người dùng thường, giữ tên thật cho Admin, làm tròn khoảng cách. */
class VenueMapperTest {

    private static final LocalDateTime TIME = LocalDateTime.of(2026, 11, 18, 7, 0);
    private final SpelAwareProxyProjectionFactory projections = new SpelAwareProxyProjectionFactory();
    private final VenueMapper mapper = Mappers.getMapper(VenueMapper.class);

    @Test
    void regularVenueMasksDeletedCreatorAndBuildsWardImagesAndVietnamLocalTimes() {
        UUID venueId = UUID.randomUUID();
        UUID wardId = UUID.randomUUID();
        VenueDetailView view = projection(VenueDetailView.class, Map.ofEntries(
                Map.entry("venueId", venueId), Map.entry("venueName", "Venue One"), Map.entry("address", "Address"),
                Map.entry("latitude", new BigDecimal("10.1234567")), Map.entry("longitude", new BigDecimal("106.1234567")),
                Map.entry("wardId", wardId), Map.entry("wardName", "Ward"), Map.entry("provinceId", UUID.randomUUID()),
                Map.entry("provinceName", "Province"), Map.entry("venueType", "SEVEN_A_SIDE"), Map.entry("status", "ACTIVE"),
                Map.entry("createdByType", "USER"), Map.entry("createdById", UUID.randomUUID()),
                Map.entry("createdByName", "Deleted Creator"), Map.entry("createdByDeleted", true),
                Map.entry("createdAt", TIME), Map.entry("updatedAt", TIME)));
        VenueImage image = VenueImage.builder().venueId(venueId).imageUrl("https://image.test/a.png")
                .publicId("folder/venue/a").isPrimary(true).displayOrder(1).uploadedBy(UUID.randomUUID()).build();

        VenueResponse response = mapper.toResponse(view, List.of(image));

        assertThat(response.venueId()).isEqualTo(venueId);
        assertThat(response.createdByName()).isEqualTo("Người dùng đã xóa");
        assertThat(response.ward().wardId()).isEqualTo(wardId);
        assertThat(response.ward().provinceName()).isEqualTo("Province");
        assertThat(response.images()).singleElement().satisfies(mapped -> {
            assertThat(mapped.imageUrl()).isEqualTo("https://image.test/a.png");
            assertThat(mapped.isPrimary()).isTrue();
            assertThat(mapped.createdAt()).isNull();
        });
        assertThat(response.createdAt()).isEqualTo(TIME);
    }

    @Test
    void adminVenueKeepsDeletedCreatorNameAndSetsFlag() {
        VenueDetailView view = projection(VenueDetailView.class, Map.ofEntries(
                Map.entry("venueId", UUID.randomUUID()), Map.entry("venueName", "Venue"), Map.entry("address", "Address"),
                Map.entry("latitude", BigDecimal.ONE), Map.entry("longitude", BigDecimal.ONE), Map.entry("status", "ACTIVE"),
                Map.entry("createdByType", "USER"), Map.entry("createdByName", "Deleted Creator"),
                Map.entry("createdByDeleted", true), Map.entry("createdAt", TIME), Map.entry("updatedAt", TIME)));

        AdminVenueResponse response = mapper.toAdminResponse(view, List.of());

        assertThat(response.createdByName()).isEqualTo("Deleted Creator");
        assertThat(response.isCreatedByDeleted()).isTrue();
    }

    @Test
    void venueSummaryRoundsDistanceToTwoDecimals() {
        VenueSummaryView view = projection(VenueSummaryView.class, Map.of(
                "venueId", UUID.randomUUID(), "venueName", "Venue", "address", "Address",
                "latitude", BigDecimal.ONE, "longitude", BigDecimal.ONE, "status", "ACTIVE",
                "distanceKm", 1.235D));

        VenueSummaryResponse response = mapper.toSummary(view);

        assertThat(response.distanceKm()).isEqualTo(new BigDecimal("1.24"));
    }

    private <T> T projection(Class<T> type, Map<String, Object> values) {
        return projections.createProjection(type, values);
    }
}
