package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.projection.PairingContextView;
import com.phananh.arenaflow.domain.projection.PairingDetailView;
import com.phananh.arenaflow.domain.projection.PairingSummaryView;
import com.phananh.arenaflow.fixture.PairingFixtures;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PairingRepositoryTest extends RepositoryIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 21, 19, 0);

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PairingRepository pairingRepository;

    @Test
    void findContextReadsPairingMatchTeamsProposalConversationAndCallerFlagsInOneQuery() {
        PairingFixtures.Scenario s = new PairingFixtures(jdbcTemplate).scenario(NOW);
        Statistics statistics = resetStatistics();

        PairingContextView ctx = pairingRepository.findContext(s.pairingId(), s.interestedCaptain()).orElseThrow();

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        assertThat(ctx.getMatchId()).isEqualTo(s.matchId());
        assertThat(ctx.getStatus()).isEqualTo("NEGOTIATING");
        assertThat(ctx.getRevision()).isZero();
        assertThat(ctx.getAuthorTeamId()).isEqualTo(s.authorTeam());
        assertThat(ctx.getMatchType()).isEqualTo("NEED_VENUE");
        assertThat(ctx.getMatchVersion()).isZero();
        assertThat(ctx.getAuthorTeamStatus()).isEqualTo("ACTIVE");
        assertThat(ctx.getSportActive()).isTrue();
        assertThat(ctx.getProposedStartTime()).isEqualTo(s.start());
        assertThat(ctx.getProposedVenueStatus()).isEqualTo("ACTIVE");
        assertThat(ctx.getConversationId()).isEqualTo(s.conversationId());
        assertThat(ctx.getCallerInterestedCaptain()).isTrue();
        assertThat(ctx.getCallerAuthorCaptain()).isFalse();
    }

    @Test
    void findDetailShowsCurrentCaptainsAndNullCaptainForDisbandedTeam() {
        PairingFixtures.Scenario s = new PairingFixtures(jdbcTemplate).scenario(NOW);
        jdbcTemplate.update("UPDATE \"user\" SET deleted_at = ? WHERE id = ?", NOW, s.authorCaptain());
        jdbcTemplate.update("UPDATE team_member SET status = 'LEFT' WHERE team_id = ?", s.interestedTeam());

        PairingDetailView detail = pairingRepository.findDetailById(s.pairingId(), s.authorCaptain(), NOW).orElseThrow();

        assertThat(detail.getAuthorCaptainName()).isEqualTo("Đội trưởng A");
        assertThat(detail.getAuthorCaptainDeleted()).isTrue();
        assertThat(detail.getInterestedCaptainName()).isNull();
        assertThat(detail.getCallerAuthorCaptain()).isTrue();
        assertThat(detail.getCallerInterestedCaptain()).isFalse();
        assertThat(detail.getProposedVenueName()).isEqualTo("Sân X");
        assertThat(detail.getConversationId()).isEqualTo(s.conversationId());
        assertThat(detail.getEffectiveStatus()).isEqualTo("NEGOTIATING");
    }

    @Test
    void readsShowExpiredWhenMatchPassedExpiryWithoutChangingDatabase() {
        PairingFixtures.Scenario s = new PairingFixtures(jdbcTemplate).scenario(NOW);
        LocalDateTime later = NOW.plusDays(2);

        assertThat(pairingRepository.findDetailById(s.pairingId(), s.authorCaptain(), later).orElseThrow()
                .getEffectiveStatus()).isEqualTo("EXPIRED");
        assertThat(pairingRepository.findByMatch(s.matchId(), "EXPIRED", later, PageRequest.of(0, 20))
                .getContent()).hasSize(1);
        assertThat(status(s.pairingId())).isEqualTo("NEGOTIATING");
    }

    @Test
    void findByMatchFiltersByEffectiveStatusAndOrdersByCreation() {
        PairingFixtures fixtures = new PairingFixtures(jdbcTemplate);
        PairingFixtures.Scenario s = fixtures.scenario(NOW);
        UUID second = fixtures.pairing(s.matchId(), fixtures.activeTeam("Gamma", fixtures.user("Gamma Captain")));
        jdbcTemplate.update("UPDATE pairing SET created_at = created_at + interval '1 second' WHERE id = ?", second);

        Page<PairingSummaryView> all = pairingRepository.findByMatch(s.matchId(), null, NOW, PageRequest.of(0, 20));
        Page<PairingSummaryView> confirmed = pairingRepository.findByMatch(s.matchId(), "CONFIRMED", NOW,
                PageRequest.of(0, 20));

        assertThat(all.getTotalElements()).isEqualTo(2);
        assertThat(all.getContent()).extracting(PairingSummaryView::getPairingId).containsExactly(s.pairingId(), second);
        assertThat(all.getContent().get(0).getConversationId()).isEqualTo(s.conversationId());
        assertThat(confirmed.getTotalElements()).isZero();
    }

    @Test
    void findByTeamFiltersByRole() {
        PairingFixtures.Scenario s = new PairingFixtures(jdbcTemplate).scenario(NOW);
        PageRequest page = PageRequest.of(0, 20);

        assertThat(pairingRepository.findByTeam(s.interestedTeam(), "INTERESTED", null, NOW, page).getTotalElements())
                .isEqualTo(1);
        assertThat(pairingRepository.findByTeam(s.interestedTeam(), "AUTHOR", null, NOW, page).getTotalElements())
                .isZero();
        assertThat(pairingRepository.findByTeam(s.authorTeam(), null, null, NOW, page).getContent())
                .extracting(PairingSummaryView::getPairingId).containsExactly(s.pairingId());
    }

    @Test
    void findProposalCandidateOnlyAcceptsOptionsOfTheSameMatch() {
        PairingFixtures fixtures = new PairingFixtures(jdbcTemplate);
        PairingFixtures.Scenario s = fixtures.scenario(NOW);
        PairingFixtures.Scenario other = fixtures.scenario(NOW);

        assertThat(pairingRepository.findProposalCandidate(s.matchId(), s.venueId(), s.timeOptionId()))
                .hasValueSatisfying(c -> assertThat(c.getVenueName()).isEqualTo("Sân X"));
        assertThat(pairingRepository.findProposalCandidate(s.matchId(), other.venueId(), s.timeOptionId())).isEmpty();
        assertThat(pairingRepository.findProposalCandidate(s.matchId(), s.venueId(), other.timeOptionId())).isEmpty();
    }

    @Test
    void updatesAreCasOnRevisionAndResetConfirmations() {
        PairingFixtures.Scenario s = new PairingFixtures(jdbcTemplate).scenario(NOW);
        jdbcTemplate.update("UPDATE pairing SET author_confirmed_at = ? WHERE id = ?", NOW, s.pairingId());

        assertThat(pairingRepository.updateCost(s.pairingId(), "VENUE_ONLY", "FIFTY_FIFTY", 500L, null, 200L, "A", 1, NOW))
                .as("revision lệch").isZero();
        assertThat(pairingRepository.updateCost(s.pairingId(), "VENUE_ONLY", "FIFTY_FIFTY", 500L, null, 200L, "A", 0, NOW))
                .isEqualTo(1);
        assertThat(pairingRepository.updateProposal(s.pairingId(), s.venueId(), s.timeOptionId(), 1, NOW)).isEqualTo(1);

        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT revision, author_confirmed_at, cost_deposit_amount "
                + "FROM pairing WHERE id = ?", s.pairingId());
        assertThat(row.get("revision")).isEqualTo(2);
        assertThat(row.get("author_confirmed_at")).isNull();
        assertThat(row.get("cost_deposit_amount")).isEqualTo(200L);
    }

    @Test
    void finalizeConfirmsChosenPairingAndSupersedesOthers() {
        PairingFixtures fixtures = new PairingFixtures(jdbcTemplate);
        PairingFixtures.Scenario s = fixtures.scenario(NOW);
        UUID other = fixtures.pairing(s.matchId(), fixtures.activeTeam("Gamma", fixtures.user("Gamma Captain")));

        assertThat(pairingRepository.finalizeConfirmation(s.matchId(), s.pairingId(), 0, NOW))
                .containsExactlyInAnyOrder(s.pairingId(), other);

        assertThat(status(s.pairingId())).isEqualTo("CONFIRMED");
        assertThat(status(other)).isEqualTo("SUPERSEDED");
        assertThat(jdbcTemplate.queryForObject("SELECT final_confirmed_at FROM pairing WHERE id = ?",
                LocalDateTime.class, s.pairingId())).isEqualTo(NOW);
    }

    /**
     * Revision lệch: pairing được chọn không đổi nhưng các pairing khác vẫn bị thay thế trong cùng câu — service phải
     * thấy kết quả thiếu id được chọn và ném lỗi để rollback cả lần thử.
     */
    @Test
    void finalizeWithStaleRevisionSkipsChosenButStillSupersedesOthersSoServiceMustRollBack() {
        PairingFixtures fixtures = new PairingFixtures(jdbcTemplate);
        PairingFixtures.Scenario s = fixtures.scenario(NOW);
        UUID other = fixtures.pairing(s.matchId(), fixtures.activeTeam("Gamma", fixtures.user("Gamma Captain")));

        assertThat(pairingRepository.finalizeConfirmation(s.matchId(), s.pairingId(), 5, NOW))
                .containsExactly(other);

        assertThat(status(s.pairingId())).isEqualTo("NEGOTIATING");
        assertThat(status(other)).isEqualTo("SUPERSEDED");
    }

    @Test
    void supersedeAndExpireTouchOnlyNegotiatingPairings() {
        PairingFixtures fixtures = new PairingFixtures(jdbcTemplate);
        PairingFixtures.Scenario cancelled = fixtures.scenario(NOW);
        PairingFixtures.Scenario expired = fixtures.scenario(NOW);

        assertThat(pairingRepository.supersedeNegotiating(cancelled.matchId(), NOW)).isEqualTo(1);
        assertThat(pairingRepository.expireNegotiatingByMatchIds(List.of(expired.matchId(), cancelled.matchId()), NOW))
                .isEqualTo(1);
        assertThat(status(cancelled.pairingId())).isEqualTo("SUPERSEDED");
        assertThat(status(expired.pairingId())).isEqualTo("EXPIRED");
    }

    @Test
    void expireByEmptyMatchIdListChangesNothing() {
        PairingFixtures.Scenario s = new PairingFixtures(jdbcTemplate).scenario(NOW);

        assertThat(pairingRepository.expireNegotiatingByMatchIds(List.of(), NOW)).isZero();
        assertThat(status(s.pairingId())).isEqualTo("NEGOTIATING");
    }

    private String status(UUID pairingId) {
        return jdbcTemplate.queryForObject("SELECT status FROM pairing WHERE id = ?", String.class, pairingId);
    }
}
