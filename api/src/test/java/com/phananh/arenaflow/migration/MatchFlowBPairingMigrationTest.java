package com.phananh.arenaflow.migration;

import com.phananh.arenaflow.fixture.PairingFixtures;
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
 * Hợp đồng hồi quy cho Flyway V8 (luồng B). {@code ddl-auto=validate} mặc định của ứng dụng kiểm entity khớp schema
 * khi context khởi động. Chạy trên database dev (.env.dev). Mỗi câu vi phạm chạy trong savepoint để transaction của
 * test vẫn dùng tiếp được.
 */
@SpringBootTest
@Transactional
class MatchFlowBPairingMigrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 21, 19, 0);

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void appliesPairingSchema() {
        assertThat(columnExists("match", "cost_deposit_amount")).isTrue();
        assertThat(columnExists("match", "confirmed_venue_id")).isFalse();
        assertThat(columnExists("match", "confirmed_match_time")).isFalse();
        assertThat(columnExists("match_venue_option", "display_order")).isTrue();
        assertThat(columnExists("pairing", "revision")).isTrue();
        assertThat(columnExists("pairing", "updated_at")).isTrue();
        assertThat(columnExists("pairing", "proposed_venue_id")).isTrue();
        assertThat(columnExists("pairing", "cost_deposit_holder")).isTrue();
        assertThat(columnExists("cost_custom_item", "pairing_id")).isTrue();
        assertThat(indexExists("idx_pairing_confirmed")).isTrue();
        assertThat(indexExists("idx_pairing_race")).isFalse();
        assertThat(indexExists("idx_match_search_gps")).isFalse();
        assertThat(indexExists("idx_cost_custom_item_pairing_order")).isTrue();
        assertThat(indexExists("idx_match_venue_option_venue")).isTrue();
    }

    @Test
    void needVenueMatchMayHaveVenueOnlyOnceMatched() {
        PairingFixtures.Scenario s = new PairingFixtures(jdbcTemplate).scenario(NOW);

        assertViolates(() -> jdbcTemplate.update("UPDATE match SET venue_id = ? WHERE id = ?", s.venueId(), s.matchId()));
        jdbcTemplate.update("UPDATE match SET venue_id = ?, confirmed_opponent_team_id = ?, status = 'CONFIRMED' "
                + "WHERE id = ?", s.venueId(), s.interestedTeam(), s.matchId());
    }

    @Test
    void depositHolderRequiresAmountAndDepositOnlyOnNeedVenueMatch() {
        PairingFixtures.Scenario s = new PairingFixtures(jdbcTemplate).scenario(NOW);

        assertViolates(() -> jdbcTemplate.update("UPDATE match SET cost_deposit_holder = 'A' WHERE id = ?", s.matchId()));
        assertViolates(() -> jdbcTemplate.update("UPDATE pairing SET cost_deposit_holder = 'A' WHERE id = ?",
                s.pairingId()));
        assertViolates(() -> jdbcTemplate.update("UPDATE pairing SET cost_deposit_amount = 0 WHERE id = ?",
                s.pairingId()));
        jdbcTemplate.update("UPDATE pairing SET cost_deposit_amount = 100, cost_deposit_holder = 'A' WHERE id = ?",
                s.pairingId());
    }

    @Test
    void proposalMustReferenceOptionsOfTheSameMatch() {
        PairingFixtures fixtures = new PairingFixtures(jdbcTemplate);
        PairingFixtures.Scenario s = fixtures.scenario(NOW);
        PairingFixtures.Scenario other = fixtures.scenario(NOW);

        assertViolates(() -> fixtures.proposal(s.pairingId(), other.venueId(), other.timeOptionId()));
        assertViolates(() -> jdbcTemplate.update("UPDATE pairing SET proposed_time_option_id = NULL WHERE id = ?",
                s.pairingId()));
    }

    @Test
    void atMostOneConfirmedPairingPerMatch() {
        PairingFixtures fixtures = new PairingFixtures(jdbcTemplate);
        PairingFixtures.Scenario s = fixtures.scenario(NOW);
        UUID other = fixtures.pairing(s.matchId(), fixtures.activeTeam("Gamma", fixtures.user("Gamma Captain")));
        fixtures.proposal(other, s.venueId(), s.timeOptionId());
        confirm(s.pairingId());

        assertViolates(() -> confirm(other));
    }

    @Test
    void costItemBelongsToExactlyOneOwner() {
        PairingFixtures.Scenario s = new PairingFixtures(jdbcTemplate).scenario(NOW);

        assertViolates(() -> item(s.matchId(), s.pairingId(), 1));
        assertViolates(() -> item(null, null, 1));
        item(null, s.pairingId(), 1);
        assertViolates(() -> item(null, s.pairingId(), 1));
    }

    private void assertViolates(Runnable statement) {
        jdbcTemplate.execute("SAVEPOINT violation");
        assertThatThrownBy(statement::run).isInstanceOf(DataIntegrityViolationException.class);
        jdbcTemplate.execute("ROLLBACK TO SAVEPOINT violation");
    }

    private void confirm(UUID pairingId) {
        jdbcTemplate.update("UPDATE pairing SET status = 'CONFIRMED', author_confirmed_at = ?, "
                + "interested_confirmed_at = ?, final_confirmed_at = ? WHERE id = ?", NOW, NOW, NOW, pairingId);
    }

    private void item(UUID matchId, UUID pairingId, int order) {
        jdbcTemplate.update("INSERT INTO cost_custom_item (id, match_id, pairing_id, item_name, amount, display_order) "
                + "VALUES (?, ?, ?, 'Trọng tài', 100, ?)", UUID.randomUUID(), matchId, pairingId, order);
    }

    private boolean columnExists(String table, String column) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT EXISTS (SELECT 1 FROM information_schema.columns "
                + "WHERE table_name = ? AND column_name = ?)", Boolean.class, table, column));
    }

    private boolean indexExists(String index) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = ?)", Boolean.class, index));
    }
}
