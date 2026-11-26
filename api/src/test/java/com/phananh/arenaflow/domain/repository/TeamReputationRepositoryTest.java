package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.application.support.reputation.ReportPenaltyPolicy;
import com.phananh.arenaflow.application.support.reputation.ReputationRules;
import com.phananh.arenaflow.domain.entity.rating.Report;
import com.phananh.arenaflow.domain.projection.ScoreChangeView;
import com.phananh.arenaflow.domain.projection.TeamReputationView;
import com.phananh.arenaflow.domain.projection.VerificationChangeView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Câu SQL uy tín đội trên PostgreSQL thật: hoàn tất trận (chuỗi trận sạch, thưởng mốc, reset bộ đếm), trừ / hoàn điểm
 * report, thống kê tỉ lệ hoàn thành + sao, refresh huy hiệu.
 */
class TeamReputationRepositoryTest extends RepositoryIntegrationTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 12, 18, 0);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 13, 9, 0);

    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private ReputationDbFixtures fixtures;
    private UUID captainA;
    private UUID captainB;
    private UUID teamA;
    private UUID teamB;
    private UUID venueId;

    @BeforeEach
    void setUp() {
        fixtures = new ReputationDbFixtures(jdbc);
        captainA = fixtures.user("Captain A", "0900000001");
        captainB = fixtures.user("Captain B", null);
        teamA = fixtures.team("Team A", captainA);
        teamB = fixtures.team("Team B", captainB);
        venueId = fixtures.venue(captainA);
    }

    @ParameterizedTest
    @CsvSource({
            "80, 0, 0, false, 0, 82, 1, 0",
            "80, 4, 2, false, 0, 87, 5, 0",
            "80, 9, 0, false, 1, 93, 10, 0",
            "80, 14, 0, false, 0, 87, 15, 0",
            "80, 3, 1, true, 0, 82, 0, 1",
            "99, 4, 0, false, 0, 100, 5, 0"
    })
    void applyCompletionFollowsCleanStreakTable(int score, int streak, int noShowCount, boolean reportedByOpponent,
                                                int bonus, int expectedScore, int expectedStreak, int expectedNoShow) {
        fixtures.teamState(teamA, score, streak, noShowCount);
        UUID matchId = fixtures.match(teamA, teamB, "ARCHIVED", START, venueId);
        if (reportedByOpponent) {
            fixtures.report(matchId, teamB, teamA, "BAD_PLAY", "AUTO_ACCEPTED", 3, captainB, START.plusHours(2));
        }

        List<ScoreChangeView> changes = applyCompletion(matchId, Map.of(teamA, bonus));

        assertThat(changes).singleElement().satisfies(change -> {
            assertThat(change.getOldScore()).isEqualTo(score);
            assertThat(change.getNewScore()).isEqualTo(expectedScore);
        });
        assertThat(column(teamA, "clean_streak")).isEqualTo(expectedStreak);
        assertThat(column(teamA, "report_count_noshow")).isEqualTo(expectedNoShow);
    }

    @Test
    void chatReportFromUnmatchedTeamDoesNotMakeMatchUnclean() {
        UUID teamY = fixtures.team("Team Y", fixtures.user("Captain Y", null));
        UUID matchId = fixtures.match(teamA, teamB, "ARCHIVED", START, venueId);
        fixtures.report(matchId, teamY, teamA, "BAD_COMMUNICATION", "AUTO_ACCEPTED", 2, captainA, START.minusDays(1));
        fixtures.teamState(teamA, 80, 0, 0);

        applyCompletion(matchId, Map.of(teamA, 0));

        assertThat(column(teamA, "clean_streak")).isEqualTo(1);
    }

    @Test
    void rejectedReportDoesNotBreakStreak() {
        UUID matchId = fixtures.match(teamA, teamB, "ARCHIVED", START, venueId);
        fixtures.report(matchId, teamB, teamA, "NOSHOW", "REJECTED", 5, captainB, START.plusHours(2));
        fixtures.teamState(teamA, 80, 2, 0);

        applyCompletion(matchId, Map.of(teamA, 0));

        assertThat(column(teamA, "clean_streak")).isEqualTo(3);
    }

    @ParameterizedTest
    @CsvSource({
            "80, 0, NOSHOW, 75, 1",
            "80, 3, NOSHOW, 65, 4",
            "80, 7, NOSHOW, 65, 8",
            "3, 0, NOSHOW, 0, 1"
    })
    void applyReportPenaltyFollowsTierTable(int score, int count, String type, int expectedScore, int expectedCount) {
        fixtures.teamState(teamA, score, 6, count);

        ScoreChangeView change = teamRepository.applyReportPenalty(teamA, type,
                ReportPenaltyPolicy.tiers(Report.ReportType.valueOf(type))).orElseThrow();

        assertThat(change.getOldScore()).isEqualTo(score);
        assertThat(change.getNewScore()).isEqualTo(expectedScore);
        assertThat(column(teamA, "report_count_noshow")).isEqualTo(expectedCount);
        assertThat(column(teamA, "clean_streak")).isZero();
    }

    @Test
    void applyReportPenaltyUsesCounterOfItsOwnType() {
        jdbc.update("UPDATE team SET report_count_bad_play = 1 WHERE id = ?", teamA);

        ScoreChangeView change = teamRepository.applyReportPenalty(teamA, "BAD_PLAY",
                ReportPenaltyPolicy.tiers(Report.ReportType.BAD_PLAY)).orElseThrow();

        assertThat(change.getNewScore()).isEqualTo(75);
        assertThat(column(teamA, "report_count_bad_play")).isEqualTo(2);
        assertThat(column(teamA, "report_count_noshow")).isZero();
    }

    @Test
    void refundRestoresPointsAndDecrementsCounterButNotBelowZeroNorStreak() {
        fixtures.teamState(teamA, 98, 0, 0);

        assertThat(teamRepository.refundReportPenalty(teamA, "NOSHOW", 5)).isEqualTo(1);

        assertThat(column(teamA, "reliability_score")).isEqualTo(100);
        assertThat(column(teamA, "report_count_noshow")).isZero();
        assertThat(column(teamA, "clean_streak")).isZero();
    }

    @Test
    void reputationStatsCountCompletedCancelledByOwnTeamLateAndArchivedNoShow() {
        UUID completed = fixtures.match(teamA, teamB, "COMPLETED", START, venueId);
        UUID completedNoShow = fixtures.match(teamB, teamA, "COMPLETED", START.plusDays(1), venueId);
        fixtures.report(completedNoShow, teamB, teamA, "NOSHOW", "AUTO_ACCEPTED", 5, captainB, START.plusDays(1));
        UUID rejectedNoShow = fixtures.match(teamA, teamB, "COMPLETED", START.plusDays(2), venueId);
        fixtures.report(rejectedNoShow, teamB, teamA, "NOSHOW", "REJECTED", 5, captainB, START.plusDays(2));
        UUID lateCancel = fixtures.match(teamA, teamB, "CONFIRMED", START.plusDays(3), venueId);
        fixtures.cancelMatch(lateCancel, teamA, START.plusDays(3).minusHours(2));
        UUID cancelledByOther = fixtures.match(teamA, teamB, "CONFIRMED", START.plusDays(4), venueId);
        fixtures.cancelMatch(cancelledByOther, teamB, START.plusDays(4).minusHours(2));
        UUID archivedNoShow = fixtures.match(teamA, teamB, "ARCHIVED", START.plusDays(5), venueId);
        fixtures.report(archivedNoShow, teamB, teamA, "NOSHOW", "AUTO_ACCEPTED", 5, captainB, START.plusDays(5));
        fixtures.rating(completed, teamB, teamA, 5, false, captainB);
        fixtures.rating(rejectedNoShow, teamB, teamA, 4, false, captainB);
        fixtures.rating(completedNoShow, teamB, teamA, null, true, captainB);

        TeamReputationView view = teamRepository.findReputation(teamA, ReputationRules.LATE_CANCEL_WINDOW_HOURS)
                .orElseThrow();

        assertThat(view.getCompletedMatches()).isEqualTo(2);
        assertThat(view.getCountedMatches()).isEqualTo(5);
        assertThat(view.getAverageRating()).isEqualByComparingTo("4.5");
        assertThat(view.getRatingCount()).isEqualTo(2);
        assertThat(view.getVerifiedAt()).isNull();
    }

    @Test
    void refreshVerificationGrantsOnceThenRevokes() {
        for (int day = 0; day < 6; day++) {
            UUID matchId = fixtures.match(teamA, teamB, "COMPLETED", START.plusDays(day), venueId);
            fixtures.rating(matchId, teamB, teamA, 4, false, captainB);
        }
        UUID[] ids = {teamA, teamB};

        List<VerificationChangeView> granted = refresh(ids);
        List<VerificationChangeView> again = refresh(ids);
        fixtures.teamState(teamA, 69, 0, 0);
        List<VerificationChangeView> revoked = refresh(ids);

        assertThat(granted).singleElement().satisfies(change -> {
            assertThat(change.getTeamId()).isEqualTo(teamA);
            assertThat(change.getVerified()).isTrue();
        });
        assertThat(again).isEmpty();
        assertThat(revoked).singleElement().satisfies(change -> assertThat(change.getVerified()).isFalse());
    }

    @Test
    void findIdsForVerificationPagesByIdAscending() {
        List<UUID> first = teamRepository.findIdsForVerification(null, 1);
        List<UUID> next = teamRepository.findIdsForVerification(first.get(0), 1);

        assertThat(next).allMatch(id -> id.compareTo(first.get(0)) > 0);
    }

    private List<ScoreChangeView> applyCompletion(UUID matchId, Map<UUID, Integer> bonuses) {
        UUID[] ids = bonuses.keySet().toArray(UUID[]::new);
        boolean[] completes = new boolean[ids.length];
        int[] bonusValues = new int[ids.length];
        for (int i = 0; i < ids.length; i++) {
            completes[i] = true;
            bonusValues[i] = bonuses.get(ids[i]);
        }
        return teamRepository.applyCompletion(matchId, ids, completes, bonusValues, ReputationRules.COMPLETION_POINTS,
                ReputationRules.STREAK_MILESTONE, ReputationRules.STREAK_BONUS_5, ReputationRules.STREAK_BONUS_10);
    }

    private List<VerificationChangeView> refresh(UUID[] ids) {
        return teamRepository.refreshVerification(ids, NOW, ReputationRules.BADGE_MIN_COMPLETED_EXCLUSIVE,
                ReputationRules.BADGE_MIN_AVG_EXCLUSIVE, ReputationRules.BADGE_MIN_SCORE,
                ReputationRules.LATE_CANCEL_WINDOW_HOURS);
    }

    private int column(UUID teamId, String column) {
        Integer value = jdbc.queryForObject("SELECT " + column + " FROM team WHERE id = ?", Integer.class, teamId);
        return value == null ? 0 : value;
    }
}
