package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.CancelMatchRequest;
import com.phananh.arenaflow.application.dto.request.ConfirmPairingRequest;
import com.phananh.arenaflow.application.dto.request.CostCustomItemRequest;
import com.phananh.arenaflow.application.dto.request.UpdatePairingCostAgreementRequest;
import com.phananh.arenaflow.application.event.match.MatchConfirmedEvent;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.time.AppTime;
import com.phananh.arenaflow.fixture.PairingFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Xác nhận đồng thời trên commit thật (không {@code @Transactional}): đúng một lần hoàn tất, thứ tự khoá kèo → pairing
 * không gây deadlock, gói đổi giữa chừng không bị ghép; huỷ kèo OPEN đẩy thương lượng sang SUPERSEDED và không ghép
 * được kèo đã huỷ. Chạy trên DB dev (.env.dev).
 */
@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class PairingConfirmRaceTest {

    private static final int ROUNDS = 5;

    @Autowired private PairingService pairingService;
    @Autowired private ConfirmedEventCounter confirmedEvents;
    @Autowired private MatchService matchService;
    @Autowired private JdbcTemplate jdbcTemplate;
    private PairingFixtures fixtures;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        fixtures = new PairingFixtures(jdbcTemplate);
        now = AppTime.now();
    }

    @AfterEach
    void cleanUp() {
        fixtures.deleteAll();
    }

    @Test
    void bothSidesConfirmingTogetherCompleteExactlyOnce() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            PairingFixtures.Scenario s = fixtures.scenario(now);

            List<String> results = runTogether(
                    () -> confirm(s.pairingId(), s.authorCaptain(), s.authorTeam(), 0),
                    () -> confirm(s.pairingId(), s.interestedCaptain(), s.interestedTeam(), 0));

            assertThat(results).containsExactlyInAnyOrder("NEGOTIATING", "CONFIRMED");
            assertThat(confirmedEvents.count(s.matchId())).isEqualTo(1);
            assertThat(matchStatus(s.matchId())).isEqualTo("CONFIRMED");
            assertThat(pairingStatus(s.pairingId())).isEqualTo("CONFIRMED");
        }
    }

    @Test
    void sameTeamInTwoPairingsConfirmingTogetherNeverDeadlocksAndConfirmsOne(CapturedOutput output) throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            PairingFixtures.Scenario p = fixtures.scenario(now);
            UUID qCaptain = fixtures.user("Q Captain");
            UUID qTeam = fixtures.activeTeam("Q", qCaptain);
            UUID q = fixtures.pairing(p.matchId(), qTeam);
            fixtures.proposal(q, p.venueId(), p.timeOptionId());
            fixtures.conversation(p.matchId(), p.authorTeam(), qTeam);
            confirm(p.pairingId(), p.interestedCaptain(), p.interestedTeam(), 0);

            List<String> results = runTogether(
                    () -> confirm(p.pairingId(), p.authorCaptain(), p.authorTeam(), 0),
                    () -> confirm(q, p.authorCaptain(), p.authorTeam(), 0),
                    () -> confirm(q, qCaptain, qTeam, 0));

            assertThat(results).doesNotContain("MATCH_014");
            assertThat(results).filteredOn("CONFIRMED"::equals).hasSize(1);
            assertThat(results).allMatch(Set.of("CONFIRMED", "NEGOTIATING", "PAIRING_005")::contains);
            assertThat(confirmedEvents.count(p.matchId())).isEqualTo(1);
            assertThat(List.of(pairingStatus(p.pairingId()), pairingStatus(q)))
                    .containsExactlyInAnyOrder("CONFIRMED", "SUPERSEDED");
        }
        assertThat(output.getAll()).doesNotContain("40P01");
    }

    @Test
    void confirmationWithStaleRevisionAfterPackageEditIsRejectedAndEditClearsConfirmation() {
        PairingFixtures.Scenario s = fixtures.scenario(now);
        confirm(s.pairingId(), s.interestedCaptain(), s.interestedTeam(), 0);

        pairingService.updateCostAgreement(s.pairingId(), s.authorCaptain(), new UpdatePairingCostAgreementRequest(
                s.authorTeam(), 0, null, null, null, null, 100_000L, "Chủ nhà",
                List.of(new CostCustomItemRequest("Trọng tài", 200_000L))));

        assertThat(jdbcTemplate.queryForObject("SELECT interested_confirmed_at IS NULL FROM pairing WHERE id = ?",
                Boolean.class, s.pairingId())).isTrue();
        assertThat(confirm(s.pairingId(), s.authorCaptain(), s.authorTeam(), 0)).isEqualTo("PAIRING_006");
    }

    @Test
    void completionWritesMatchCopiesItemsAndMarksVenueSelected() {
        PairingFixtures.Scenario s = fixtures.scenario(now);
        pairingService.updateCostAgreement(s.pairingId(), s.authorCaptain(), new UpdatePairingCostAgreementRequest(
                s.authorTeam(), 0, null, null, null, null, 100_000L, "Chủ nhà",
                List.of(new CostCustomItemRequest("Trọng tài", 200_000L), new CostCustomItemRequest("Nước", 50_000L))));
        confirm(s.pairingId(), s.interestedCaptain(), s.interestedTeam(), 1);

        assertThat(confirm(s.pairingId(), s.authorCaptain(), s.authorTeam(), 1)).isEqualTo("CONFIRMED");

        assertThat(jdbcTemplate.queryForList("SELECT item_name FROM cost_custom_item WHERE match_id = ? "
                + "ORDER BY display_order", String.class, s.matchId())).containsExactly("Trọng tài", "Nước");
        assertThat(jdbcTemplate.queryForObject("SELECT venue_id FROM match WHERE id = ?", UUID.class, s.matchId()))
                .isEqualTo(s.venueId());
        assertThat(jdbcTemplate.queryForObject("SELECT last_selected_at IS NOT NULL FROM venue WHERE id = ?",
                Boolean.class, s.venueId())).isTrue();
    }

    @Test
    void confirmingOnOverdueMatchExpiresMatchAndPairingThenReportsMatch004() {
        PairingFixtures.Scenario s = fixtures.scenario(now);
        jdbcTemplate.update("UPDATE match SET expires_at = ? WHERE id = ?", now.minusMinutes(1), s.matchId());

        assertThat(confirm(s.pairingId(), s.authorCaptain(), s.authorTeam(), 0)).isEqualTo("MATCH_004");
        assertThat(matchStatus(s.matchId())).isEqualTo("EXPIRED");
        assertThat(pairingStatus(s.pairingId())).isEqualTo("EXPIRED");
    }

    @Test
    void cancellingOpenNeedVenueSupersedesNegotiatingPairings() {
        PairingFixtures.Scenario s = fixtures.scenario(now);

        assertThat(cancelOpen(s)).isEqualTo("CANCELLED");

        assertThat(matchStatus(s.matchId())).isEqualTo("CANCELLED");
        assertThat(pairingStatus(s.pairingId())).isEqualTo("SUPERSEDED");
    }

    @Test
    void cancelOpenRacingCompletionEndsCancelledWithSupersededOrConfirmedWithConfirmed(CapturedOutput output)
            throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            PairingFixtures.Scenario s = fixtures.scenario(now);
            confirm(s.pairingId(), s.interestedCaptain(), s.interestedTeam(), 0);

            List<String> results = runTogether(
                    () -> cancelOpen(s),
                    () -> confirm(s.pairingId(), s.authorCaptain(), s.authorTeam(), 0));

            assertThat(results).doesNotContain("MATCH_014");
            assertThat(List.of(matchStatus(s.matchId()), pairingStatus(s.pairingId())))
                    .isIn(List.of("CANCELLED", "SUPERSEDED"), List.of("CONFIRMED", "CONFIRMED"));
        }
        assertThat(output.getAll()).doesNotContain("40P01");
    }

    private String cancelOpen(PairingFixtures.Scenario s) {
        try {
            return matchService.cancelMatch(s.matchId(), s.authorCaptain(),
                    new CancelMatchRequest(s.authorTeam(), "OPEN", "Đổi lịch")).status();
        } catch (BusinessException exception) {
            return exception.getErrorCode().getCode();
        }
    }

    private String confirm(UUID pairingId, UUID callerId, UUID teamId, int revision) {
        try {
            return pairingService.confirm(pairingId, callerId, new ConfirmPairingRequest(teamId, revision)).status();
        } catch (BusinessException exception) {
            return exception.getErrorCode().getCode();
        }
    }

    @SafeVarargs
    private List<String> runTogether(Callable<String>... tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.length);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (Callable<String> task : tasks) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<String> results = new ArrayList<>();
            for (Future<String> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private String matchStatus(UUID matchId) {
        return jdbcTemplate.queryForObject("SELECT status FROM match WHERE id = ?", String.class, matchId);
    }

    private String pairingStatus(UUID pairingId) {
        return jdbcTemplate.queryForObject("SELECT status FROM pairing WHERE id = ?", String.class, pairingId);
    }

    /** Đăng ký bộ đếm event ghép kèo cho test này. */
    @TestConfiguration
    static class ConfirmedEventsConfig {

        @Bean
        ConfirmedEventCounter confirmedEventCounter() {
            return new ConfirmedEventCounter();
        }
    }

    /** Đếm {@link MatchConfirmedEvent} theo kèo, chỉ tính lần thử đã commit (lần thử rollback không phát). */
    static class ConfirmedEventCounter {

        private final Map<UUID, AtomicInteger> perMatch = new ConcurrentHashMap<>();

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        void on(MatchConfirmedEvent event) {
            perMatch.computeIfAbsent(event.matchId(), id -> new AtomicInteger()).incrementAndGet();
        }

        int count(UUID matchId) {
            AtomicInteger count = perMatch.get(matchId);
            return count == null ? 0 : count.get();
        }
    }
}
