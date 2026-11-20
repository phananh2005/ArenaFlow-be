package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.domain.entity.user.User;
import com.phananh.arenaflow.domain.projection.StaleVenueView;
import com.phananh.arenaflow.domain.repository.UserRepository;
import com.phananh.arenaflow.domain.repository.VenueRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chứng minh native {@code UPDATE … RETURNING} khai báo không có {@code @Modifying} chạy được qua Spring Data và
 * trả về projection, đồng thời chỉ claim địa điểm ACTIVE đã quá ngưỡng và chưa được cảnh báo.
 * Database dev có thể chứa địa điểm lâu không được chọn khác nên assertion chỉ xét fixture của test này.
 */
@SpringBootTest
@Transactional
class VenueStaleAlertRepositoryTest {

    private static final int LARGE_LIMIT = 100_000;

    @Autowired private VenueRepository venueRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void markStaleAlertedClaimsOnlyEligibleVenuesOnceAndReturnsIdAndName() {
        OffsetDateTime now = OffsetDateTime.parse("2026-11-19T03:00:00Z");
        OffsetDateTime threshold = now.minusDays(180);
        UUID creator = userRepository.saveAndFlush(User.builder().displayName("Stale Creator").build()).getId();
        UUID neverSelectedOld = venue(creator, "Stale Never Selected", "ACTIVE", now.minusDays(200), null, null);
        UUID selectedLongAgo = venue(creator, "Stale Selected Long Ago", "ACTIVE", now.minusDays(400), now.minusDays(181), null);
        UUID selectedRecently = venue(creator, "Fresh Selected", "ACTIVE", now.minusDays(400), now.minusDays(10), null);
        UUID createdRecently = venue(creator, "Fresh Created", "ACTIVE", now.minusDays(10), null, null);
        UUID inactive = venue(creator, "Stale Inactive", "INACTIVE", now.minusDays(400), null, null);
        UUID alreadyAlerted = venue(creator, "Stale Alerted", "ACTIVE", now.minusDays(400), null, now.minusDays(1));
        List<UUID> fixtures = List.of(neverSelectedOld, selectedLongAgo, selectedRecently, createdRecently, inactive,
                alreadyAlerted);

        List<StaleVenueView> claimed = venueRepository.markStaleAlerted(threshold, now, LARGE_LIMIT).stream()
                .filter(view -> fixtures.contains(view.getId()))
                .toList();

        assertThat(claimed).extracting(StaleVenueView::getId).containsExactlyInAnyOrder(neverSelectedOld, selectedLongAgo);
        assertThat(claimed).extracting(StaleVenueView::getName)
                .containsExactlyInAnyOrder("Stale Never Selected", "Stale Selected Long Ago");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM venue WHERE id IN (?, ?) AND stale_alerted_at IS NOT NULL",
                Integer.class, neverSelectedOld, selectedLongAgo)).isEqualTo(2);
        assertThat(venueRepository.markStaleAlerted(threshold, now, LARGE_LIMIT).stream()
                .filter(view -> fixtures.contains(view.getId()))).as("second run claims nothing").isEmpty();
    }

    @Test
    void markStaleAlertedHonoursBatchLimit() {
        OffsetDateTime now = OffsetDateTime.parse("2026-11-19T03:00:00Z");
        UUID creator = userRepository.saveAndFlush(User.builder().displayName("Stale Limit Creator").build()).getId();
        venue(creator, "Stale Limit A", "ACTIVE", now.minusDays(300), null, null);
        venue(creator, "Stale Limit B", "ACTIVE", now.minusDays(300), null, null);

        assertThat(venueRepository.markStaleAlerted(now.minusDays(180), now, 1)).hasSize(1);
    }

    private UUID venue(UUID creator, String name, String status, OffsetDateTime createdAt, OffsetDateTime lastSelectedAt,
                       OffsetDateTime staleAlertedAt) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO venue (id, name, address, latitude, longitude, status, created_by_type, "
                        + "created_by_user_id, created_at, updated_at, last_selected_at, stale_alerted_at, admin_verified_inactive_at) "
                        + "VALUES (?, ?, 'Address', 10.1, 106.1, ?, 'USER', ?, ?, ?, ?, ?, ?)",
                id, name, status, creator, createdAt, createdAt, lastSelectedAt, staleAlertedAt,
                "INACTIVE".equals(status) ? createdAt : null);
        return id;
    }
}
