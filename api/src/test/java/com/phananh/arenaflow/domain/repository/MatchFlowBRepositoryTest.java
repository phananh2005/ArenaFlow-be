package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.application.support.GeoBoundingBox;
import com.phananh.arenaflow.domain.entity.match.CostCustomItem;
import com.phananh.arenaflow.domain.projection.MatchDetailView;
import com.phananh.arenaflow.domain.projection.MatchOptionRowView;
import com.phananh.arenaflow.domain.projection.MatchSummaryView;
import com.phananh.arenaflow.fixture.PairingFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lựa chọn của kèo B, khoản tự nhập của thương lượng, CAS ghép kèo trên {@code match}, và kèo B trong tìm kèo / kèo của
 * đội / chi tiết kèo.
 */
class MatchFlowBRepositoryTest extends RepositoryIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 21, 19, 0);

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MatchVenueOptionRepository matchVenueOptionRepository;
    @Autowired private CostCustomItemRepository costCustomItemRepository;
    @Autowired private MatchRepository matchRepository;
    @Autowired private PairingRepository pairingRepository;

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

    /** Kèo B có 2 địa điểm (Sân X ở 10.1/106.1, Sân Y ở 10.2/106.2) và 2 khung giờ (start, start + 1 ngày). */
    private PairingFixtures.Scenario twoOptionScenario(PairingFixtures fixtures) {
        PairingFixtures.Scenario s = fixtures.scenario(NOW);
        UUID second = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO venue (id, name, address, latitude, longitude, created_by_type, "
                + "created_by_user_id) VALUES (?, 'Sân Y', 'Địa chỉ', 10.2, 106.2, 'USER', ?)", second, s.authorCaptain());
        fixtures.venueOption(s.matchId(), second, 2);
        fixtures.timeOption(s.matchId(), s.start().plusDays(1), s.start().plusDays(1).plusHours(2));
        jdbcTemplate.update("UPDATE sport SET is_active = TRUE WHERE id = ?", fixtures.sportId());
        return s;
    }

    private static Optional<MatchSummaryView> find(List<MatchSummaryView> rows, UUID matchId) {
        return rows.stream().filter(row -> row.getMatchId().equals(matchId)).findFirst();
    }

    @Test
    void searchMatchesNeedVenueByAnyTimeOptionAndCountsOptions() {
        PairingFixtures fixtures = new PairingFixtures(jdbcTemplate);
        PairingFixtures.Scenario s = twoOptionScenario(fixtures);
        LocalDateTime second = s.start().plusDays(1);
        PageRequest page = PageRequest.of(0, 100);

        Optional<MatchSummaryView> bySecondSlot = find(matchRepository.searchOpen("NEED_VENUE", fixtures.sportId(), null,
                null, second.minusHours(1), second.plusHours(1), NOW, page).getContent(), s.matchId());
        assertThat(bySecondSlot).as("khớp khung giờ thứ hai dù giờ trận = khung sớm nhất").isPresent();
        assertThat(bySecondSlot.get().getVenueId()).isNull();
        assertThat(bySecondSlot.get().getVenueOptionCount()).isEqualTo(2);
        assertThat(bySecondSlot.get().getTimeOptionCount()).isEqualTo(2);
        assertThat(find(matchRepository.searchOpen(null, fixtures.sportId(), null, null, second.plusHours(1), null, NOW,
                page).getContent(), s.matchId())).as("không khung nào từ mốc này").isEmpty();
    }

    @Test
    void searchNearbyMeasuresNeedVenueToNearestVenueOption() {
        PairingFixtures fixtures = new PairingFixtures(jdbcTemplate);
        PairingFixtures.Scenario s = twoOptionScenario(fixtures);
        PageRequest page = PageRequest.of(0, 100);
        GeoBoundingBox nearY = GeoBoundingBox.around(10.2, 106.2, 1);
        GeoBoundingBox nowhere = GeoBoundingBox.around(10.5, 106.5, 1);

        Optional<MatchSummaryView> found = find(matchRepository.searchOpenNearby("NEED_VENUE", fixtures.sportId(), null,
                null, null, null, NOW, 10.2, 106.2, 1, nearY.minLat(), nearY.maxLat(), nearY.minLng(), nearY.maxLng(),
                page).getContent(), s.matchId());
        assertThat(found).isPresent();
        assertThat(found.get().getDistanceKm()).isLessThan(0.01);
        assertThat(find(matchRepository.searchOpenNearby(null, null, null, null, null, null, NOW, 10.5, 106.5, 1,
                nowhere.minLat(), nowhere.maxLat(), nowhere.minLng(), nowhere.maxLng(), page).getContent(),
                s.matchId())).isEmpty();
    }

    @Test
    void teamMatchesCountOptionsOfNeedVenue() {
        PairingFixtures fixtures = new PairingFixtures(jdbcTemplate);
        PairingFixtures.Scenario s = twoOptionScenario(fixtures);

        MatchSummaryView row = find(matchRepository.findTeamMatches(s.authorTeam(), "AUTHOR", null, NOW,
                PageRequest.of(0, 20)).getContent(), s.matchId()).orElseThrow();

        assertThat(row.getVenueOptionCount()).isEqualTo(2);
        assertThat(row.getTimeOptionCount()).isEqualTo(2);
    }

    @Test
    void detailReadsConfirmedPairingAndDepositOfNeedVenue() {
        PairingFixtures.Scenario s = new PairingFixtures(jdbcTemplate).scenario(NOW);

        MatchDetailView open = matchRepository.findDetailById(s.matchId(), NOW).orElseThrow();
        assertThat(open.getConfirmedPairingId()).isNull();
        assertThat(open.getCostDepositAmount()).isNull();
        assertThat(open.getVenueId()).isNull();

        matchRepository.confirmPairing(s.matchId(), 0L, s.interestedTeam(), s.venueId(), s.start(),
                s.start().plusHours(2), "VENUE_ONLY", "FIFTY_FIFTY", 600_000L, null, 200_000L, "Chủ nhà", NOW);
        pairingRepository.finalizeConfirmation(s.matchId(), s.pairingId(), 0, NOW);

        MatchDetailView confirmed = matchRepository.findDetailById(s.matchId(), NOW).orElseThrow();
        assertThat(confirmed.getConfirmedPairingId()).isEqualTo(s.pairingId());
        assertThat(confirmed.getCostDepositAmount()).isEqualTo(200_000L);
        assertThat(confirmed.getCostDepositHolder()).isEqualTo("Chủ nhà");
        assertThat(confirmed.getVenueId()).isEqualTo(s.venueId());
    }
}
