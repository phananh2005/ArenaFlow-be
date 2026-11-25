package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.projection.ConfirmSideResult;
import com.phananh.arenaflow.domain.projection.TimeOptionView;
import com.phananh.arenaflow.fixture.PairingFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cổng chặn đầu giai đoạn tích hợp: native {@code INSERT … RETURNING} / {@code UPDATE … RETURNING} khai báo không
 * {@code @Modifying} chạy được qua Spring Data và trả projection; bind mảng {@code uuid[]} / {@code timestamp[]} vào
 * {@code unnest}. Fail → dừng, hỏi người dùng (phương án dự phòng làm vượt ngưỡng số truy vấn).
 */
class PairingReturningSpikeTest extends RepositoryIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 21, 19, 0);

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MatchTimeOptionRepository matchTimeOptionRepository;
    @Autowired private MatchVenueOptionRepository matchVenueOptionRepository;
    @Autowired private PairingRepository pairingRepository;

    @Test
    void insertReturningBindsTimestampArraysAndReturnsGeneratedIds() {
        PairingFixtures fixtures = new PairingFixtures(jdbcTemplate);
        UUID captain = fixtures.user("Spike");
        UUID team = fixtures.activeTeam("Spike Team", captain);
        LocalDateTime start = NOW.plusDays(3);
        UUID matchId = fixtures.needVenueMatch(team, start, start.plusHours(2), NOW.plusDays(1));

        List<TimeOptionView> rows = matchTimeOptionRepository.insertAllReturning(matchId,
                new LocalDateTime[] {start, start.plusDays(1)},
                new LocalDateTime[] {start.plusHours(2), start.plusDays(1).plusHours(2)}, NOW);

        assertThat(rows).hasSize(2).allSatisfy(row -> assertThat(row.getId()).isNotNull());
        assertThat(rows).extracting(TimeOptionView::getProposedStartTime).containsExactly(start, start.plusDays(1));
        assertThat(rows.get(0).getId().version()).isEqualTo(7);
    }

    @Test
    void insertBindsUuidArrayInOrder() {
        PairingFixtures fixtures = new PairingFixtures(jdbcTemplate);
        UUID captain = fixtures.user("Spike Venue");
        UUID team = fixtures.activeTeam("Spike Venue Team", captain);
        LocalDateTime start = NOW.plusDays(3);
        UUID matchId = fixtures.needVenueMatch(team, start, start.plusHours(2), NOW.plusDays(1));
        UUID first = fixtures.venue(captain, "Spike 1", "ACTIVE", NOW.minusDays(1));
        UUID second = fixtures.venue(captain, "Spike 2", "ACTIVE", NOW.minusDays(1));

        int inserted = matchVenueOptionRepository.insertAll(matchId, new UUID[] {second, first}, NOW);

        assertThat(inserted).isEqualTo(2);
        assertThat(jdbcTemplate.queryForList("SELECT venue_id FROM match_venue_option WHERE match_id = ? "
                + "ORDER BY display_order", UUID.class, matchId)).containsExactly(second, first);
    }

    @Test
    void updateReturningConfirmSideReturnsBothConfirmationTimes() {
        PairingFixtures.Scenario s = new PairingFixtures(jdbcTemplate).scenario(NOW);

        Optional<ConfirmSideResult> result = pairingRepository.confirmSide(s.pairingId(), "AUTHOR", 0, NOW);

        assertThat(result).hasValueSatisfying(r -> {
            assertThat(r.getAuthorConfirmedAt()).isEqualTo(NOW);
            assertThat(r.getInterestedConfirmedAt()).isNull();
        });
        assertThat(pairingRepository.confirmSide(s.pairingId(), "AUTHOR", 0, NOW)).as("đã xác nhận → 0 dòng").isEmpty();
    }
}
