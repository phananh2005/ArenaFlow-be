package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.projection.AdminDisputeDetailView;
import com.phananh.arenaflow.domain.projection.AdminDisputeView;
import com.phananh.arenaflow.domain.projection.DisputeContextView;
import com.phananh.arenaflow.domain.projection.MatchRatingRowView;
import com.phananh.arenaflow.domain.projection.RatingContextView;
import com.phananh.arenaflow.domain.projection.ReportContextView;
import com.phananh.arenaflow.domain.projection.ReportDecisionView;
import com.phananh.arenaflow.domain.projection.TeamReportView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Câu ngữ cảnh / danh sách / cập nhật có điều kiện của đánh giá, report, tranh chấp trên PostgreSQL thật. */
class ReputationContextRepositoryTest extends RepositoryIntegrationTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 12, 18, 0);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 13, 9, 0);

    @Autowired
    private MatchRepository matchRepository;
    @Autowired
    private RatingRepository ratingRepository;
    @Autowired
    private ReportRepository reportRepository;
    @Autowired
    private DisputeRepository disputeRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private ReputationDbFixtures fixtures;
    private UUID captainA;
    private UUID captainB;
    private UUID teamA;
    private UUID teamB;
    private UUID matchId;

    @BeforeEach
    void setUp() {
        fixtures = new ReputationDbFixtures(jdbc);
        captainA = fixtures.user("Captain A", null);
        captainB = fixtures.user("Captain B", null);
        teamA = fixtures.team("Team A", captainA);
        teamB = fixtures.team("Team B", captainB);
        matchId = fixtures.match(teamA, teamB, "ARCHIVED", START, fixtures.venue(captainA));
    }

    @Test
    void ratingContextFlagsCallerSideAndRatedSides() {
        fixtures.rating(matchId, teamB, teamA, 4, false, captainB);

        RatingContextView ctx = matchRepository.findRatingContext(matchId, captainB).orElseThrow();

        assertThat(ctx.getStatus()).isEqualTo("ARCHIVED");
        assertThat(ctx.getCallerAuthorCaptain()).isFalse();
        assertThat(ctx.getCallerOpponentCaptain()).isTrue();
        assertThat(ctx.getAuthorRated()).isFalse();
        assertThat(ctx.getOpponentRated()).isTrue();
        assertThat(ctx.getAuthorTeamStatus()).isEqualTo("ACTIVE");
        assertThat(matchRepository.findRatingContext(UUID.randomUUID(), captainA)).isEmpty();
    }

    @Test
    void reportContextFlagsAlreadyReportedTypePerDirection() {
        fixtures.report(matchId, teamA, teamB, "NOSHOW", "AUTO_ACCEPTED", 5, captainA, START.plusHours(2));

        ReportContextView ctx = matchRepository.findReportContext(matchId, captainA, "NOSHOW").orElseThrow();

        assertThat(ctx.getAuthorReportedType()).isTrue();
        assertThat(ctx.getOpponentReportedType()).isFalse();
        assertThat(ctx.getOpponentTeamName()).isEqualTo("Team B");
        assertThat(matchRepository.findReportContext(matchId, captainA, "BAD_PLAY").orElseThrow().getAuthorReportedType())
                .isFalse();
    }

    @Test
    void completeIfArchivedTransitionsOnce() {
        assertThat(matchRepository.completeIfArchived(matchId, NOW)).isEqualTo(1);
        assertThat(matchRepository.completeIfArchived(matchId, NOW)).isZero();
    }

    @Test
    void matchRatingRowsReturnOneEmptyRowWhenNoRating() {
        List<MatchRatingRowView> rows = ratingRepository.findMatchRatingRows(matchId);

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.getAuthorTeamId()).isEqualTo(teamA);
            assertThat(row.getRatingId()).isNull();
        });
        assertThat(ratingRepository.findMatchRatingRows(UUID.randomUUID())).isEmpty();
    }

    @Test
    void teamReportsSplitByDirectionWithDispute() {
        UUID reportId = fixtures.report(matchId, teamA, teamB, "NOSHOW", "DISPUTED", 5, captainA, START.plusHours(2));
        fixtures.dispute(reportId, captainB, START.plusHours(3));

        List<TeamReportView> received = reportRepository.findReceivedReports(teamB, 20, 0);
        List<TeamReportView> sent = reportRepository.findSentReports(teamA, 20, 0);

        assertThat(received).singleElement().satisfies(view -> {
            assertThat(view.getCounterpartTeamId()).isEqualTo(teamA);
            assertThat(view.getDisputeStatus()).isEqualTo("PENDING");
        });
        assertThat(sent).singleElement().satisfies(view -> assertThat(view.getCounterpartTeamId()).isEqualTo(teamB));
        assertThat(reportRepository.countReceivedReports(teamB)).isEqualTo(1);
        assertThat(reportRepository.countSentReports(teamB)).isZero();
    }

    @Test
    void markDisputedChecksStatusTypeAndDeadlineInTheSameStatement() {
        LocalDateTime createdAt = NOW.minusHours(23);
        UUID noShow = fixtures.report(matchId, teamA, teamB, "NOSHOW", "AUTO_ACCEPTED", 5, captainA, createdAt);
        UUID badPlay = fixtures.report(matchId, teamA, teamB, "BAD_PLAY", "AUTO_ACCEPTED", 3, captainA, createdAt);

        assertThat(reportRepository.markDisputed(noShow, NOW, NOW.minusHours(24))).isEqualTo(1);
        assertThat(reportRepository.markDisputed(noShow, NOW, NOW.minusHours(24))).isZero();
        assertThat(reportRepository.markDisputed(badPlay, NOW, NOW.minusHours(24))).isZero();
        UUID expired = fixtures.report(matchId, teamB, teamA, "NOSHOW", "AUTO_ACCEPTED", 5, captainB, NOW.minusHours(24));
        assertThat(reportRepository.markDisputed(expired, NOW, NOW.minusHours(24))).isZero();
    }

    @Test
    void disputeContextReadsReportedTeamAndCallerCaptainFlag() {
        UUID reportId = fixtures.report(matchId, teamA, teamB, "NOSHOW", "AUTO_ACCEPTED", 5, captainA, START);

        DisputeContextView ctx = reportRepository.findDisputeContext(reportId, captainB).orElseThrow();

        assertThat(ctx.getReportedTeamId()).isEqualTo(teamB);
        assertThat(ctx.getCallerReportedCaptain()).isTrue();
        assertThat(ctx.getDisputeExists()).isFalse();
    }

    @Test
    void resolveDisputeAndApplyDecisionAreConditional() {
        UUID reportId = fixtures.report(matchId, teamA, teamB, "NOSHOW", "DISPUTED", 5, captainA, START);
        UUID disputeId = fixtures.dispute(reportId, captainB, START.plusHours(1));
        UUID adminId = fixtures.user("Admin", null);

        assertThat(disputeRepository.resolveIfPending(disputeId, "REJECTED", "Ghi chú", adminId, NOW)).contains(reportId);
        assertThat(disputeRepository.resolveIfPending(disputeId, "REJECTED", "Ghi chú", adminId, NOW)).isEmpty();
        ReportDecisionView decision = reportRepository.applyDecision(reportId, "REJECTED", NOW).orElseThrow();
        assertThat(decision.getReportedTeamId()).isEqualTo(teamB);
        assertThat(decision.getReliabilityPenalty()).isEqualTo(5);
        assertThat(reportRepository.applyDecision(reportId, "REJECTED", NOW)).isEmpty();
    }

    @Test
    void adminDisputesShowRealNamesWithDeletedFlagAndOrder() {
        UUID first = fixtures.dispute(fixtures.report(matchId, teamA, teamB, "NOSHOW", "DISPUTED", 5, captainA, START),
                captainB, START.plusHours(1));
        UUID second = fixtures.dispute(fixtures.report(matchId, teamB, teamA, "NOSHOW", "DISPUTED", 5, captainB, START),
                captainA, START.plusHours(2));
        fixtures.deleteUser(captainB, NOW);

        List<AdminDisputeView> oldest = disputeRepository.findAdminDisputesOldestFirst("PENDING", 100, 0);
        List<AdminDisputeView> newest = disputeRepository.findAdminDisputesNewestFirst(null, 100, 0);
        AdminDisputeDetailView detail = disputeRepository.findAdminDisputeDetail(first).orElseThrow();

        assertThat(oldest).extracting(AdminDisputeView::getDisputeId).containsSubsequence(first, second);
        assertThat(newest).extracting(AdminDisputeView::getDisputeId).containsSubsequence(second, first);
        assertThat(detail.getDisputedByName()).isEqualTo("Captain B");
        assertThat(detail.getDisputedByDeleted()).isTrue();
        assertThat(detail.getResolvedByDeleted()).isFalse();
        assertThat(detail.getSportName()).isNotBlank();
        assertThat(disputeRepository.countAdminDisputes("PENDING")).isGreaterThanOrEqualTo(2);
    }
}
