package com.phananh.arenaflow.migration;

import com.phananh.arenaflow.domain.repository.ReputationDbFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Hợp đồng hồi quy cho Flyway V9 (đánh giá, report, tranh chấp, huy hiệu). Chạy trên database dev qua .env.dev, sau
 * khi các migration đứng trước đã có.
 */
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Transactional
class ReputationMigrationTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 12, 18, 0);

    @Autowired
    private JdbcTemplate jdbc;

    private ReputationDbFixtures fixtures;
    private UUID captainA;
    private UUID teamA;
    private UUID teamB;
    private UUID matchId;

    @BeforeEach
    void setUp() {
        fixtures = new ReputationDbFixtures(jdbc);
        captainA = fixtures.user("Captain A", null);
        UUID captainB = fixtures.user("Captain B", null);
        teamA = fixtures.team("Team A", captainA);
        teamB = fixtures.team("Team B", captainB);
        matchId = fixtures.match(teamA, teamB, "ARCHIVED", START, fixtures.venue(captainA));
    }

    @Test
    void appliesReputationSchema() {
        assertThat(columnExists("rating", "is_no_show")).isTrue();
        assertThat(columnExists("rating", "rater_team_score")).isTrue();
        assertThat(columnExists("rating", "created_by_user_id")).isTrue();
        assertThat(columnExists("report", "reliability_penalty")).isTrue();
        assertThat(columnExists("report", "updated_at")).isTrue();
        assertThat(columnExists("report", "admin_reviewed_at")).isFalse();
        assertThat(columnExists("team", "verified_at")).isTrue();
        assertThat(columnExists("team", "total_matches_completed")).isFalse();
        assertThat(columnExists("team", "average_rating")).isFalse();
        assertThat(columnExists("user", "total_matches_confirmed")).isFalse();
        assertThat(indexExists("idx_report_unique")).isTrue();
        assertThat(indexExists("idx_report_reported_created")).isTrue();
        assertThat(indexExists("idx_report_reporter_created")).isTrue();
        assertThat(indexExists("idx_dispute_report")).isTrue();
        assertThat(indexExists("idx_dispute_status_created")).isTrue();
        assertThat(indexExists("idx_report_status")).isFalse();
    }

    @Test
    void noShowRatingCannotCarryScore() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO rating (id, match_id, rater_type, rater_id, ratee_type, ratee_id, score, is_no_show,
                                    created_by_user_id)
                VALUES (?, ?, 'TEAM', ?, 'TEAM', ?, 5, TRUE, ?)
                """, UUID.randomUUID(), matchId, teamA, teamB, captainA))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("rating_no_show_check");
    }

    @Test
    void teamScoresMustComeInPairs() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO rating (id, match_id, rater_type, rater_id, ratee_type, ratee_id, score, rater_team_score,
                                    created_by_user_id)
                VALUES (?, ?, 'TEAM', ?, 'TEAM', ?, 4, 3, ?)
                """, UUID.randomUUID(), matchId, teamA, teamB, captainA))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("rating_team_scores_pair_check");
    }

    @Test
    void duplicateReportOfSameTypeIsRejectedByUniqueIndex() {
        fixtures.report(matchId, teamA, teamB, "NOSHOW", "AUTO_ACCEPTED", 5, captainA, START.plusHours(3));

        assertThatThrownBy(() -> fixtures.report(matchId, teamA, teamB, "NOSHOW", "AUTO_ACCEPTED", 5, captainA,
                START.plusHours(4)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("idx_report_unique");
    }

    @Test
    void reportStatusAcceptsOnlyDisputeLifecycle() {
        assertThatThrownBy(() -> fixtures.report(matchId, teamA, teamB, "NOSHOW", "PENDING", 0, captainA, START))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("report_status_check");
    }

    @Test
    void resolvedDisputeNeedsDecisionNoteAdminAndTime() {
        UUID reportId = fixtures.report(matchId, teamA, teamB, "NOSHOW", "DISPUTED", 5, captainA, START);
        UUID disputeId = fixtures.dispute(reportId, captainA, START.plusHours(1));

        assertViolates("dispute_resolution_check",
                () -> jdbc.update("UPDATE dispute SET status = 'RESOLVED' WHERE id = ?", disputeId));
        assertViolates("idx_dispute_report", () -> fixtures.dispute(reportId, captainA, START.plusHours(2)));
    }

    /** Câu vi phạm chạy trong savepoint để transaction của test vẫn dùng tiếp được cho câu sau. */
    private void assertViolates(String constraint, Runnable statement) {
        jdbc.execute("SAVEPOINT violation");
        assertThatThrownBy(statement::run)
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(constraint);
        jdbc.execute("ROLLBACK TO SAVEPOINT violation");
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?
                """, Integer.class, table, column);
        return count != null && count > 0;
    }

    private boolean indexExists(String index) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = current_schema() AND indexname = ?",
                Integer.class, index);
        return count != null && count > 0;
    }
}
