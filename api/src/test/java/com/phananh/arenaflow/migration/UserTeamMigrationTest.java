package com.phananh.arenaflow.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

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
