package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.entity.match.CostCustomItem;
import com.phananh.arenaflow.domain.projection.MatchOptionRowView;
import com.phananh.arenaflow.fixture.PairingFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Lựa chọn của kèo B, khoản tự nhập của thương lượng và CAS ghép kèo trên {@code match}. */
class MatchFlowBRepositoryTest extends RepositoryIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 21, 19, 0);

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MatchVenueOptionRepository matchVenueOptionRepository;
    @Autowired private CostCustomItemRepository costCustomItemRepository;
    @Autowired private MatchRepository matchRepository;

    @Test
    void findOptionRowsReturnsVenuesInInputOrderAndTimesInOneQuery() {
        PairingFixtures fixtures = new PairingFixtures(jdbcTemplate);
        PairingFixtures.Scenario s = fixtures.scenario(NOW);
        UUID second = fixtures.venue(s.authorCaptain(), "Sân Y", "INACTIVE", NOW.minusDays(1));
        fixtures.venueOption(s.matchId(), second, 2);

        List<MatchOptionRowView> rows = matchVenueOptionRepository.findOptionRows(s.matchId());

        assertThat(rows).filteredOn(r -> "VENUE".equals(r.getKind())).extracting(MatchOptionRowView::getVenueId)
                .containsExactly(s.venueId(), second);
        assertThat(rows).filteredOn(r -> "TIME".equals(r.getKind())).extracting(MatchOptionRowView::getTimeOptionId)
                .containsExactly(s.timeOptionId());
    }

    @Test
    void pairingItemsAreReplacedInOrderAndCopiedToMatchOnConfirmation() {
        PairingFixtures.Scenario s = new PairingFixtures(jdbcTemplate).scenario(NOW);

        costCustomItemRepository.insertAllForPairing(s.pairingId(), new String[] {"Trọng tài", "Bóng"},
                new Long[] {200_000L, 50_000L}, NOW);
        costCustomItemRepository.deleteByPairingIdBulk(s.pairingId());
        costCustomItemRepository.insertAllForPairing(s.pairingId(), new String[] {"Nước", "Trọng tài"},
                new Long[] {30_000L, 200_000L}, NOW);
        int copied = costCustomItemRepository.copyPairingItemsToMatch(s.pairingId(), s.matchId(), NOW);

        List<CostCustomItem> pairingItems = costCustomItemRepository.findByPairingIdOrderByDisplayOrderAsc(s.pairingId());
        assertThat(pairingItems).extracting(CostCustomItem::getItemName).containsExactly("Nước", "Trọng tài");
        assertThat(copied).isEqualTo(2);
        List<Map<String, Object>> matchItems = jdbcTemplate.queryForList("SELECT id, item_name, pairing_id FROM "
                + "cost_custom_item WHERE match_id = ? ORDER BY display_order", s.matchId());
        assertThat(matchItems).extracting(r -> r.get("item_name")).containsExactly("Nước", "Trọng tài");
        assertThat(matchItems).allSatisfy(r -> assertThat(r.get("pairing_id")).isNull());
        assertThat(matchItems).extracting(r -> r.get("id")).doesNotContainAnyElementsOf(
                pairingItems.stream().map(CostCustomItem::getId).toList());
    }

    @Test
    void copyWithoutPairingItemsInsertsNothing() {
        PairingFixtures.Scenario s = new PairingFixtures(jdbcTemplate).scenario(NOW);

        assertThat(costCustomItemRepository.copyPairingItemsToMatch(s.pairingId(), s.matchId(), NOW)).isZero();
    }

    @Test
    void confirmPairingIsCasOnOpenStatusAndVersionAndWritesAgreedPackage() {
        PairingFixtures.Scenario s = new PairingFixtures(jdbcTemplate).scenario(NOW);
        LocalDateTime start = s.start();

        assertThat(matchRepository.confirmPairing(s.matchId(), 1L, s.interestedTeam(), s.venueId(), start,
                start.plusHours(2), "VENUE_ONLY", "FIFTY_FIFTY", 600_000L, null, 200_000L, "Chủ nhà", NOW))
                .as("version lệch").isZero();
        assertThat(matchRepository.confirmPairing(s.matchId(), 0L, s.interestedTeam(), s.venueId(), start,
                start.plusHours(2), "VENUE_ONLY", "FIFTY_FIFTY", 600_000L, null, 200_000L, "Chủ nhà", NOW))
                .isEqualTo(1);

        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT status, venue_id, confirmed_opponent_team_id, "
                + "cost_deposit_amount, cost_deposit_holder, version FROM match WHERE id = ?", s.matchId());
        assertThat(row.get("status")).isEqualTo("CONFIRMED");
        assertThat(row.get("venue_id")).isEqualTo(s.venueId());
        assertThat(row.get("confirmed_opponent_team_id")).isEqualTo(s.interestedTeam());
        assertThat(row.get("cost_deposit_amount")).isEqualTo(200_000L);
        assertThat(row.get("cost_deposit_holder")).isEqualTo("Chủ nhà");
        assertThat(row.get("version")).isEqualTo(1L);
    }
}
