package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.support.reputation.TeamVerificationRefresher;
import com.phananh.arenaflow.domain.repository.ReputationDbFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Refresh huy hiệu sau commit chạy được trong transaction mới (REQUIRES_NEW) gọi từ {@code afterCommit}, và không chạy
 * khi transaction gốc rollback. Dữ liệu commit thật → tự dọn sau mỗi test.
 */
@SpringBootTest
class TeamVerificationRefresherIntegrationTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 1, 18, 0);

    @Autowired
    private TeamVerificationRefresher refresher;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private JdbcTemplate jdbc;

    private final List<UUID> teams = new ArrayList<>();
    private final List<UUID> users = new ArrayList<>();
    private final List<UUID> matches = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        matches.forEach(id -> {
            jdbc.update("DELETE FROM rating WHERE match_id = ?", id);
            jdbc.update("DELETE FROM match WHERE id = ?", id);
        });
        jdbc.update("DELETE FROM venue WHERE created_by_user_id = ANY(?)", (Object) users.toArray(UUID[]::new));
        teams.forEach(id -> jdbc.update("DELETE FROM team WHERE id = ?", id));
        users.forEach(id -> jdbc.update("DELETE FROM \"user\" WHERE id = ?", id));
    }

    @Test
    void refreshesInNewTransactionAfterCommitAndSkipsRollback() {
        UUID teamA = eligibleTeam();

        try {
            transactionTemplate.executeWithoutResult(status -> {
                refresher.refreshAfterCommit(List.of(teamA));
                throw new IllegalStateException("rollback");
            });
        } catch (IllegalStateException expected) {
            // lần thử rollback: không refresh
        }
        assertThat(verifiedAt(teamA)).isNull();

        transactionTemplate.executeWithoutResult(status -> refresher.refreshAfterCommit(List.of(teamA)));

        assertThat(verifiedAt(teamA)).isNotNull();
    }

    private UUID eligibleTeam() {
        ReputationDbFixtures fixtures = new ReputationDbFixtures(jdbc);
        UUID captainA = fixtures.user("Captain A", "0900000009");
        UUID captainB = fixtures.user("Captain B", null);
        users.addAll(List.of(captainA, captainB));
        UUID teamA = fixtures.team("Team A", captainA);
        UUID teamB = fixtures.team("Team B", captainB);
        teams.addAll(List.of(teamA, teamB));
        UUID venueId = fixtures.venue(captainA);
        for (int day = 0; day < 6; day++) {
            UUID matchId = fixtures.match(teamA, teamB, "COMPLETED", START.plusDays(day), venueId);
            matches.add(matchId);
            fixtures.rating(matchId, teamB, teamA, 5, false, captainB);
        }
        return teamA;
    }

    private LocalDateTime verifiedAt(UUID teamId) {
        return jdbc.queryForObject("SELECT verified_at FROM team WHERE id = ?", LocalDateTime.class, teamId);
    }
}
