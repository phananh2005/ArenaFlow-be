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

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=none")
class UserTeamMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void appliesUserTeamManagementSchema() {
        assertThat(columnExists("user", "role")).isTrue();
        assertThat(columnExists("user", "ward_id")).isTrue();
        assertThat(columnExists("team", "skill_levels")).isTrue();
        assertThat(columnExists("team", "inactive_reason")).isTrue();
        assertThat(columnExists("team", "version")).isTrue();
        assertThat(columnExists("team_member", "version")).isTrue();
        assertThat(columnExists("team_captain_history", "reason")).isTrue();
        assertThat(tableExists("team_tag")).isFalse();
    }

    @Test
    void appliesTeamDisbandSchema() {
        assertThat(columnExists("team", "disband_reason")).isTrue();
        assertThat(columnExists("team", "disbanded_at")).isTrue();
        assertThat(columnExists("team", "disbanded_by")).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT character_maximum_length FROM information_schema.columns " +
                        "WHERE table_schema = current_schema() AND table_name = 'team' AND column_name = 'disband_reason'",
                Integer.class)).isEqualTo(100);
    }

    @Test
    @Transactional
    void acceptsDisbandedTeamWithFullMetadata() {
        UUID userId = insertUser();

        int inserted = jdbcTemplate.update(
                "INSERT INTO team (id, name, created_by, status, disband_reason, disbanded_at, disbanded_by) " +
                        "VALUES (?, 'Disband Migration Team', ?, 'DISBANDED', 'Đội trưởng giải tán đội', NOW(), ?)",
                UUID.randomUUID(), userId, userId);

        assertThat(inserted).isEqualTo(1);
    }

    @Test
    @Transactional
    void rejectsDisbandedTeamWithoutMetadata() {
        UUID userId = insertUser();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO team (id, name, created_by, status) VALUES (?, 'Disband Migration Team', ?, 'DISBANDED')",
                UUID.randomUUID(), userId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("team_disband_consistency_check");
    }

    @Test
    @Transactional
    void rejectsNonDisbandedTeamWithDisbandMetadata() {
        UUID userId = insertUser();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO team (id, name, created_by, status, disband_reason) " +
                        "VALUES (?, 'Disband Migration Team', ?, 'ACTIVE', 'Đội trưởng giải tán đội')",
                UUID.randomUUID(), userId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("team_disband_consistency_check");
    }

    private UUID insertUser() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO \"user\" (id, display_name) VALUES (?, 'Disband Migration User')", userId);
        return userId;
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                        "WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?",
                Integer.class,
                table,
                column);
        return count != null && count > 0;
    }

    private boolean tableExists(String table) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables " +
                        "WHERE table_schema = current_schema() AND table_name = ?",
                Integer.class,
                table);
        return count != null && count > 0;
    }
}
