package com.phananh.arenaflow.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression contract for Flyway V5. Runs against the dev database configured by .env.dev.
 */
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=none")
class VenueManagementMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void legacyReportsWillNotConflictWithV5PendingReportUniqueIndex() {
        Integer duplicateGroups = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM (
                    SELECT reporter_id, venue_id
                    FROM venue_report
                    GROUP BY reporter_id, venue_id
                    HAVING COUNT(*) > 1
                ) duplicates
                """, Integer.class);

        assertThat(duplicateGroups).isZero();
    }

    @Test
    void appliesVenueManagementSchema() {
        assertThat(columnExists("venue", "version")).isTrue();
        assertThat(columnExists("venue", "stale_alerted_at")).isTrue();
        assertThat(columnExists("venue", "closure_alerted_at")).isTrue();
        assertThat(columnExists("venue", "ward_id")).isTrue();
        assertThat(columnExists("venue_image", "public_id")).isTrue();
        assertThat(columnExists("venue_report", "detail")).isTrue();
        assertThat(columnExists("venue_report", "status")).isTrue();
        assertThat(columnExists("venue_report", "handled_by")).isTrue();
        assertThat(columnExists("venue_report", "handled_at")).isTrue();
        assertThat(indexExists("idx_venue_status_name")).isTrue();
        assertThat(indexExists("uq_venue_image_public_id")).isTrue();
        assertThat(indexExists("idx_venue_report_status_created")).isTrue();
        assertThat(indexExists("uq_venue_report_pending")).isTrue();
        assertThat(indexExists("idx_venue_ward")).isTrue();
    }

    @Test
    @Transactional
    void enforcesVenueTypeConstraint() {
        UUID venueId = insertVenue(insertUser());

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE venue SET venue_type = 'INVALID' WHERE id = ?", venueId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("venue_type_check");
    }

    @Test
    @Transactional
    void enforcesInactiveVenueConsistencyConstraint() {
        UUID venueId = insertVenue(insertUser());

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE venue SET status = 'INACTIVE', admin_verified_inactive_at = NULL WHERE id = ?", venueId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("venue_inactive_consistency_check");
    }

    @Test
    @Transactional
    void enforcesUniqueVenueImagePublicId() {
        UUID venueId = insertVenue(insertUser());
        jdbcTemplate.update("""
                INSERT INTO venue_image (id, venue_id, image_url, display_order, public_id)
                VALUES (?, ?, 'https://example.test/venue.png', 1, 'test/unique-public-id')
                """, UUID.randomUUID(), venueId);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO venue_image (id, venue_id, image_url, display_order, public_id)
                VALUES (?, ?, 'https://example.test/venue-duplicate.png', 2, 'test/unique-public-id')
                """, UUID.randomUUID(), venueId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_venue_image_public_id");
    }

    @Test
    @Transactional
    void enforcesSinglePendingReportPerReporterVenueAndReason() {
        UUID userId = insertUser();
        UUID venueId = insertVenue(userId);
        jdbcTemplate.update("""
                INSERT INTO venue_report (id, venue_id, reporter_id, reason, status)
                VALUES (?, ?, ?, 'OTHER', 'PENDING')
                """, UUID.randomUUID(), venueId, userId);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO venue_report (id, venue_id, reporter_id, reason, status)
                VALUES (?, ?, ?, 'OTHER', 'PENDING')
                """, UUID.randomUUID(), venueId, userId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_venue_report_pending");
    }

    private UUID insertUser() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO \"user\" (id, display_name) VALUES (?, 'Venue Migration User')", userId);
        return userId;
    }

    private UUID insertVenue(UUID userId) {
        UUID venueId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO venue (id, name, address, latitude, longitude, created_by_type, created_by_user_id)
                VALUES (?, 'Venue Migration', 'Test address', 10.0, 106.0, 'USER', ?)
                """, venueId, userId);
        return venueId;
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?
                """, Integer.class, table, column);
        return count != null && count > 0;
    }

    private boolean indexExists(String index) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pg_indexes WHERE schemaname = current_schema() AND indexname = ?",
                Integer.class, index);
        return count != null && count > 0;
    }
}
