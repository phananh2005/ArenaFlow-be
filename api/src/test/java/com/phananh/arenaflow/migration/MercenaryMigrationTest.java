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
 * Hợp đồng hồi quy cho Flyway V10 (tuyển mercenary, trận ngoài hệ thống). Chạy trên database dev sau V6–V9; trước khi áp
 * V10 lên môi trường có dữ liệu phải kiểm {@code mercenary_recruitment} không có dòng (cột mới NOT NULL không mặc định).
 */
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=none")
class MercenaryMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void appliesMercenarySchema() {
        assertThat(columnExists("external_match", "version")).isTrue();
        assertThat(columnExists("mercenary_recruitment", "external_match_id")).isTrue();
        assertThat(columnExists("mercenary_recruitment", "created_by_user_id")).isTrue();
        assertThat(columnExists("mercenary_recruitment", "closed_at")).isTrue();
        assertThat(columnExists("mercenary_application", "game_cancel_notified_at")).isTrue();
        assertThat(columnExists("rating", "application_id")).isTrue();
        assertThat(columnExists("report", "external_match_id")).isTrue();
        assertThat(columnExists("user", "verified_at")).isTrue();
        assertThat(columnExists("conversation", "application_id")).isTrue();
        assertThat(indexExists("idx_mercenary_recruitment_match_position")).isTrue();
        assertThat(indexExists("idx_mercenary_recruitment_external_position")).isTrue();
        assertThat(indexExists("idx_mercenary_application_cancel_notice")).isTrue();
        assertThat(indexExists("idx_rating_mercenary_unique")).isTrue();
        assertThat(indexExists("idx_report_external_unique")).isTrue();
        assertThat(indexExists("idx_conversation_application")).isTrue();
        assertThat(indexExists("idx_external_match_cancelled")).isTrue();
        assertThat(indexExists("idx_mercenary_recruitment_search")).isFalse();
    }

    @Test
    @Transactional
    void recruitmentNeedsExactlyOneGameAndClosedTimestampMatchesStatus() {
        UUID userId = insertUser();
        UUID teamId = insertTeam(userId);
        UUID externalMatchId = insertExternalMatch(teamId, insertVenue(userId), userId);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO mercenary_recruitment (id, match_id, external_match_id, team_id, position, quantity_needed,
                                                   created_by_user_id)
                VALUES (?, NULL, NULL, ?, 'Tiền vệ', 1, ?)
                """, UUID.randomUUID(), teamId, userId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("mercenary_recruitment_game_check");
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO mercenary_recruitment (id, external_match_id, team_id, position, quantity_needed,
                                                   created_by_user_id, status)
                VALUES (?, ?, ?, 'Tiền vệ', 1, ?, 'CLOSED')
                """, UUID.randomUUID(), externalMatchId, teamId, userId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("mercenary_recruitment_closed_check");
    }

    @Test
    @Transactional
    void onlyOneOpenRecruitmentPerExternalMatchAndPosition() {
        UUID userId = insertUser();
        UUID teamId = insertTeam(userId);
        UUID externalMatchId = insertExternalMatch(teamId, insertVenue(userId), userId);
        String insert = """
                INSERT INTO mercenary_recruitment (id, external_match_id, team_id, position, quantity_needed,
                                                   created_by_user_id)
                VALUES (?, ?, ?, 'Thủ môn', 1, ?)
                """;
        jdbcTemplate.update(insert, UUID.randomUUID(), externalMatchId, teamId, userId);

        assertThatThrownBy(() -> jdbcTemplate.update(insert, UUID.randomUUID(), externalMatchId, teamId, userId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("idx_mercenary_recruitment_external_position");
    }

    @Test
    @Transactional
    void externalMatchCancelledTimestampMatchesStatus() {
        UUID userId = insertUser();
        UUID teamId = insertTeam(userId);
        UUID venueId = insertVenue(userId);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO external_match (id, team_id, venue_id, match_start_time, match_end_time, status,
                                            created_by_user_id)
                VALUES (?, ?, ?, TIMESTAMP '2030-01-01 18:00', TIMESTAMP '2030-01-01 19:30', 'CANCELLED', ?)
                """, UUID.randomUUID(), teamId, venueId, userId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("external_match_cancel_check");
    }

    private UUID insertUser() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO \"user\" (id, display_name) VALUES (?, 'Mercenary Migration User')", userId);
        return userId;
    }

    private UUID insertTeam(UUID userId) {
        UUID teamId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO team (id, name, created_by) VALUES (?, 'Mercenary Migration Team', ?)", teamId,
                userId);
        return teamId;
    }

    private UUID insertVenue(UUID userId) {
        UUID venueId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO venue (id, name, address, latitude, longitude, created_by_type, created_by_user_id)
                VALUES (?, 'Mercenary Venue', 'Test address', 10.0, 106.0, 'USER', ?)
                """, venueId, userId);
        return venueId;
    }

    private UUID insertExternalMatch(UUID teamId, UUID venueId, UUID userId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO external_match (id, team_id, venue_id, match_start_time, match_end_time, created_by_user_id)
                VALUES (?, ?, ?, TIMESTAMP '2030-01-01 18:00', TIMESTAMP '2030-01-01 19:30', ?)
                """, id, teamId, venueId, userId);
        return id;
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?
                """, Integer.class, table, column);
        return count != null && count > 0;
    }

    private boolean indexExists(String index) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = current_schema() AND indexname = ?",
                Integer.class, index);
        return count != null && count > 0;
    }
}
