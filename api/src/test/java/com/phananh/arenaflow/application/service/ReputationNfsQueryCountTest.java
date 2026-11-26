package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.CreateDisputeRequest;
import com.phananh.arenaflow.application.dto.request.CreateRatingRequest;
import com.phananh.arenaflow.application.dto.request.CreateReportRequest;
import com.phananh.arenaflow.application.dto.request.ResolveDisputeRequest;
import com.phananh.arenaflow.common.time.AppTime;
import com.phananh.arenaflow.domain.repository.ReputationDbFixtures;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Đếm số câu SQL thật (Hibernate Statistics) của các luồng đánh giá / report / tranh chấp / uy tín. Test chạy trong
 * transaction rollback nên câu refresh huy hiệu sau commit KHÔNG nằm trong số đếm — tổng của request = số đếm + 1 với
 * các luồng có refresh (2.1, 2.3, 2.8 bác report).
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class ReputationNfsQueryCountTest {

    private static final int DETAIL_LIMIT = 3;
    private static final int LIST_LIMIT = 4;
    private static final int WRITE_LIMIT = 6;
    private static final int AFTER_COMMIT_REFRESH = 1;
    private static final LocalDateTime START = LocalDateTime.of(2026, 1, 10, 18, 0);

    @Autowired private RatingService ratingService;
    @Autowired private ReportService reportService;
    @Autowired private DisputeService disputeService;
    @Autowired private AdminDisputeService adminDisputeService;
    @Autowired private TeamReputationService teamReputationService;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private JdbcTemplate jdbc;

    private Statistics statistics;
    private ReputationDbFixtures fixtures;
    private UUID captainA;
    private UUID captainB;
    private UUID teamA;
    private UUID teamB;
    private UUID venueId;

    @BeforeEach
    void setUp() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        fixtures = new ReputationDbFixtures(jdbc);
        captainA = fixtures.user("NFS Captain A", null);
        captainB = fixtures.user("NFS Captain B", null);
        teamA = fixtures.team("NFS Team A", captainA);
        teamB = fixtures.team("NFS Team B", captainB);
        venueId = fixtures.venue(captainA);
    }

    @Test
    void ratingAndReportWritesStayWithinWriteBudget() {
        UUID archived = fixtures.match(teamA, teamB, "ARCHIVED", START, venueId);
        assertThat(measure(() -> ratingService.createRating(archived, captainA,
                new CreateRatingRequest(false, 5, null, null, null))) + AFTER_COMMIT_REFRESH)
                .as("POST /matches/{id}/ratings hoàn tất trận").isEqualTo(6).isLessThanOrEqualTo(WRITE_LIMIT);

        assertThat(measure(() -> reportService.createReport(archived, captainB,
                new CreateReportRequest("NOSHOW", "Không đến"))) + AFTER_COMMIT_REFRESH)
                .as("POST /matches/{id}/reports").isEqualTo(5).isLessThanOrEqualTo(WRITE_LIMIT);

        assertThat(measure(() -> ratingService.getMatchRatings(archived, teamA, captainA)))
                .as("GET /matches/{id}/ratings").isEqualTo(2).isLessThanOrEqualTo(DETAIL_LIMIT);

        assertThat(measure(() -> reportService.listTeamReports(teamA, captainA, ReportDirection.RECEIVED, 1, 20)))
                .as("GET /teams/{id}/reports").isEqualTo(3).isLessThanOrEqualTo(LIST_LIMIT);

        assertThat(measure(() -> teamReputationService.getTeamReputation(teamA)))
                .as("GET /teams/{id}/reputation").isEqualTo(1).isLessThanOrEqualTo(DETAIL_LIMIT);
    }

    @Test
    void disputeAndAdminPathsStayWithinBudgets() {
        UUID matchId = fixtures.match(teamA, teamB, "COMPLETED", START, venueId);
        UUID reportId = fixtures.report(matchId, teamA, teamB, "NOSHOW", "AUTO_ACCEPTED", 5, captainA,
                AppTime.now().minusHours(1));
        UUID adminId = fixtures.user("NFS Admin", null);

        assertThat(measure(() -> disputeService.createDispute(reportId, captainB, new CreateDisputeRequest("Có mặt"))))
                .as("POST /reports/{id}/dispute").isEqualTo(3).isLessThanOrEqualTo(WRITE_LIMIT);
        UUID disputeId = jdbc.queryForObject("SELECT id FROM dispute WHERE report_id = ?", UUID.class, reportId);

        assertThat(measure(() -> adminDisputeService.listDisputes(DisputeStatusFilter.PENDING, 1, 20)))
                .as("GET /admin/disputes").isEqualTo(2).isLessThanOrEqualTo(LIST_LIMIT);

        assertThat(measure(() -> adminDisputeService.getDispute(disputeId)))
                .as("GET /admin/disputes/{id}").isEqualTo(2).isLessThanOrEqualTo(DETAIL_LIMIT);

        assertThat(measure(() -> adminDisputeService.resolveDispute(disputeId, adminId,
                new ResolveDisputeRequest("REJECTED", "Có mặt đúng giờ"))) + AFTER_COMMIT_REFRESH)
                .as("PATCH /admin/disputes/{id}/resolve REJECTED").isEqualTo(6).isLessThanOrEqualTo(WRITE_LIMIT);
    }

    /** Flush + clear trước và sau để đếm cả câu ghi lúc flush (vd UPDATE điểm thực trừ của report). */
    private long measure(Supplier<?> operation) {
        entityManager.flush();
        entityManager.clear();
        statistics.clear();
        operation.get();
        entityManager.flush();
        return statistics.getPrepareStatementCount();
    }
}
