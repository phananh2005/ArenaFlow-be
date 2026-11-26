package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.entity.team.Team;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Sửa đội qua dirty checking không ghi đè cột uy tín / huy hiệu đã được câu UPDATE nguyên tử thay đổi. */
class ReputationEntityDirtyCheckTest extends RepositoryIntegrationTest {

    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void editingTeamKeepsReputationColumnsWrittenByAtomicUpdates() {
        ReputationDbFixtures fixtures = new ReputationDbFixtures(jdbc);
        UUID teamId = fixtures.team("Team A", fixtures.user("Captain", null));
        Team team = teamRepository.findById(teamId).orElseThrow();
        jdbc.update("""
                UPDATE team SET reliability_score = 55, clean_streak = 3, report_count_noshow = 2, verified_at = ?
                WHERE id = ?
                """, LocalDateTime.of(2026, 11, 13, 9, 0), teamId);

        team.setName("Team A đổi tên");
        flushAndClear();

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT name, reliability_score, clean_streak, report_count_noshow, verified_at FROM team WHERE id = ?",
                teamId);
        assertThat(row.get("name")).isEqualTo("Team A đổi tên");
        assertThat(row.get("reliability_score")).isEqualTo(55);
        assertThat(row.get("clean_streak")).isEqualTo(3);
        assertThat(row.get("report_count_noshow")).isEqualTo(2);
        assertThat(row.get("verified_at")).isNotNull();
    }
}
