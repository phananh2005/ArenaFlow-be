package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.CreateDisputeRequest;
import com.phananh.arenaflow.application.dto.request.CreateRatingRequest;
import com.phananh.arenaflow.application.dto.request.CreateReportRequest;
import com.phananh.arenaflow.application.dto.request.ResolveDisputeRequest;
import com.phananh.arenaflow.application.support.reputation.ReportPenaltyPolicy;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.common.time.AppTime;
import com.phananh.arenaflow.domain.entity.rating.Report;
import com.phananh.arenaflow.domain.entity.user.User;
import com.phananh.arenaflow.domain.projection.ScoreChangeView;
import com.phananh.arenaflow.domain.repository.ReputationDbFixtures;
import com.phananh.arenaflow.domain.repository.TeamRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Đánh giá / report đồng thời trên dữ liệu đã commit: chỉ một lần hoàn tất trận (không cộng điểm hoàn thành hai lần),
 * report trùng đồng thời bị chặn bởi index chống trùng (không trừ điểm hai lần).
 */
class ReputationRaceTest extends CommittedDataIntegrationTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 1, 10, 18, 0);

    private static final long LOCK_WAIT_TIMEOUT_MS = 10_000;

    @Autowired private RatingService ratingService;
    @Autowired private ReportService reportService;
    @Autowired private DisputeService disputeService;
    @Autowired private AdminDisputeService adminDisputeService;
    @Autowired private ReputationService reputationService;
    @Autowired private TeamRepository teamRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private final List<UUID> matches = new ArrayList<>();

    /** Chạy trước dọn dẹp của lớp cha: xoá dữ liệu trận + sân để xoá được đội và user. */
    @AfterEach
    void deleteMatchData() {
        matches.forEach(id -> {
            UUID venueId = jdbcTemplate.queryForObject("SELECT venue_id FROM match WHERE id = ?", UUID.class, id);
            jdbcTemplate.update("DELETE FROM dispute WHERE report_id IN (SELECT id FROM report WHERE match_id = ?)", id);
            jdbcTemplate.update("DELETE FROM rating WHERE match_id = ?", id);
            jdbcTemplate.update("DELETE FROM report WHERE match_id = ?", id);
            jdbcTemplate.update("DELETE FROM match WHERE id = ?", id);
            jdbcTemplate.update("DELETE FROM venue WHERE id = ?", venueId);
        });
    }

    @Test
    void bothCaptainsRatingArchivedMatchTogetherCompleteItOnce() throws Exception {
        User captainA = user("Race Captain A");
        User captainB = user("Race Captain B");
        UUID teamA = team(captainA);
        UUID teamB = team(captainB);
        ReputationDbFixtures fixtures = new ReputationDbFixtures(jdbcTemplate);
        UUID matchId = fixtures.match(teamA, teamB, "ARCHIVED", START, fixtures.venue(captainA.getId()));
        matches.add(matchId);

        List<Object> results = race(
                () -> ratingService.createRating(matchId, captainA.getId(), new CreateRatingRequest(false, 4, null, null, null)),
                () -> ratingService.createRating(matchId, captainB.getId(), new CreateRatingRequest(false, 4, null, null, null)));

        assertThat(results).allMatch(result -> !(result instanceof Throwable));
        assertThat(score(teamA)).isEqualTo(82);
        assertThat(score(teamB)).isEqualTo(82);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM match WHERE id = ?", String.class, matchId))
                .isEqualTo("COMPLETED");
    }

    @Test
    void duplicateReportsTogetherPenalizeOnce() throws Exception {
        User captainA = user("Race Reporter A");
        User captainB = user("Race Reported B");
        UUID teamA = team(captainA);
        UUID teamB = team(captainB);
        ReputationDbFixtures fixtures = new ReputationDbFixtures(jdbcTemplate);
        UUID matchId = fixtures.match(teamA, teamB, "COMPLETED", START, fixtures.venue(captainA.getId()));
        matches.add(matchId);

        List<Object> results = race(
                () -> reportService.createReport(matchId, captainA.getId(), new CreateReportRequest("BAD_PLAY", "Chơi xấu")),
                () -> reportService.createReport(matchId, captainA.getId(), new CreateReportRequest("BAD_PLAY", "Chơi xấu")));

        assertThat(results).filteredOn(BusinessException.class::isInstance).hasSize(1);
        assertThat(score(teamB)).isEqualTo(77);
    }

    /** Hai Admin bác cùng một tranh chấp cùng lúc: một bên quyết định, bên kia DISPUTE_002; điểm hoàn đúng một lần. */
    @Test
    void twoAdminsRejectingSameDisputeTogetherRefundOnce() throws Exception {
        User captainA = user("Dispute Reporter A");
        User captainB = user("Dispute Reported B");
        UUID teamA = team(captainA);
        UUID teamB = team(captainB);
        ReputationDbFixtures fixtures = new ReputationDbFixtures(jdbcTemplate);
        UUID matchId = fixtures.match(teamA, teamB, "COMPLETED", START, fixtures.venue(captainA.getId()));
        matches.add(matchId);
        UUID reportId = fixtures.report(matchId, teamA, teamB, "NOSHOW", "DISPUTED", 5, captainA.getId(),
                START.plusHours(3));
        jdbcTemplate.update("UPDATE team SET reliability_score = 75, report_count_noshow = 1 WHERE id = ?", teamB);
        UUID disputeId = fixtures.dispute(reportId, captainB.getId(), START.plusHours(4));
        User firstAdmin = user("Race Admin 1", User.Role.ADMIN);
        User secondAdmin = user("Race Admin 2", User.Role.ADMIN);

        List<Object> results = race(
                () -> adminDisputeService.resolveDispute(disputeId, firstAdmin.getId(),
                        new ResolveDisputeRequest("REJECTED", "Có mặt đúng giờ")),
                () -> adminDisputeService.resolveDispute(disputeId, secondAdmin.getId(),
                        new ResolveDisputeRequest("REJECTED", "Có mặt đúng giờ")));

        assertThat(results).filteredOn(result -> !(result instanceof Throwable)).hasSize(1);
        assertThat(results).filteredOn(BusinessException.class::isInstance)
                .singleElement()
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.DISPUTE_002));
        assertThat(score(teamB)).isEqualTo(80);
        assertThat(count("SELECT report_count_noshow FROM team WHERE id = ?", teamB)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM report WHERE id = ?", String.class, reportId))
                .isEqualTo("REJECTED");
    }

    /**
     * Hai yêu cầu tranh chấp cùng một report: câu CAS của bên sau chờ khoá rồi không khớp trạng thái → thử lại → guard
     * trả REPORT_008; chỉ một tranh chấp được tạo.
     */
    @Test
    void twoDisputesOfSameReportTogetherCreateOne() throws Exception {
        User captainA = user("Dispute Race Reporter");
        User captainB = user("Dispute Race Reported");
        UUID teamA = team(captainA);
        UUID teamB = team(captainB);
        ReputationDbFixtures fixtures = new ReputationDbFixtures(jdbcTemplate);
        UUID matchId = fixtures.match(teamA, teamB, "COMPLETED", START, fixtures.venue(captainA.getId()));
        matches.add(matchId);
        UUID reportId = fixtures.report(matchId, teamA, teamB, "NOSHOW", "AUTO_ACCEPTED", 5, captainA.getId(),
                AppTime.now().minusHours(1));

        List<Object> results = race(
                () -> disputeService.createDispute(reportId, captainB.getId(), new CreateDisputeRequest("Có mặt")),
                () -> disputeService.createDispute(reportId, captainB.getId(), new CreateDisputeRequest("Có mặt")));

        assertThat(results).filteredOn(result -> !(result instanceof Throwable)).hasSize(1);
        assertThat(results).filteredOn(BusinessException.class::isInstance)
                .singleElement()
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.REPORT_008));
        assertThat(count("SELECT COUNT(*) FROM dispute WHERE report_id = ?", reportId)).isEqualTo(1);
    }

    /**
     * Giao dịch 1 trừ điểm NOSHOW và giữ khoá dòng đội; giao dịch 2 bắt đầu câu lệnh (chụp ảnh lúc bộ đếm còn 0) rồi
     * chờ khoá; giao dịch 1 chỉ commit khi thấy session của giao dịch 2 đang chờ. Giao dịch 2 phải tính bậc từ bộ đếm
     * mới (lần 2 → trừ 8), không từ ảnh chụp đầu câu (lần 1 → trừ 5).
     */
    @Test
    void sameTypePenaltiesAtOnceUseConsecutiveTiers() throws Exception {
        UUID teamId = team(user("Penalty Race Captain"));
        jdbcTemplate.update("UPDATE team SET reliability_score = 80, report_count_noshow = 0 WHERE id = ?", teamId);
        int[] tiers = ReportPenaltyPolicy.tiers(Report.ReportType.NOSHOW);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstApplied = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        try {
            Future<ScoreChangeView> first = executor.submit(() -> tx.execute(status -> {
                ScoreChangeView change = teamRepository.applyReportPenalty(teamId, "NOSHOW", tiers).orElseThrow();
                firstApplied.countDown();
                awaitQuietly(releaseFirst);
                return change;
            }));
            assertThat(firstApplied.await(LOCK_WAIT_TIMEOUT_MS, MILLISECONDS)).isTrue();

            CompletableFuture<Integer> secondSession = new CompletableFuture<>();
            Future<ScoreChangeView> second = executor.submit(() -> tx.execute(status -> {
                // Cùng connection với câu trừ điểm bên dưới: transaction gắn connection này vào thread hiện tại.
                secondSession.complete(jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Integer.class));
                return teamRepository.applyReportPenalty(teamId, "NOSHOW", tiers).orElseThrow();
            }));
            awaitBlockedOnLock(secondSession.get(LOCK_WAIT_TIMEOUT_MS, MILLISECONDS));
            releaseFirst.countDown();

            ScoreChangeView firstChange = first.get(30, SECONDS);
            ScoreChangeView secondChange = second.get(30, SECONDS);
            assertThat(firstChange.getOldScore() - firstChange.getNewScore()).isEqualTo(5);
            assertThat(secondChange.getOldScore()).isEqualTo(75);
            assertThat(secondChange.getOldScore() - secondChange.getNewScore()).isEqualTo(8);
            assertThat(score(teamId)).isEqualTo(67);
            assertThat(count("SELECT report_count_noshow FROM team WHERE id = ?", teamId)).isEqualTo(2);
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    /**
     * Giao dịch 1 ghi report bùng cho đội B (trừ điểm, chuỗi về 0, giữ khoá dòng đội B); giao dịch 2 hoàn tất trận chờ
     * khoá đội B; giao dịch 1 commit khi thấy giao dịch 2 đang chờ. Hoàn tất trận phải thấy report đó: trận của B không
     * "sạch" nên chuỗi giữ 0 (không thành 1), bộ đếm bùng giữ 1.
     */
    @Test
    void completionWaitingOnReportPenaltySeesTheReport() throws Exception {
        User captainA = user("Completion Race A");
        User captainB = user("Completion Race B");
        UUID teamA = team(captainA);
        UUID teamB = team(captainB);
        ReputationDbFixtures fixtures = new ReputationDbFixtures(jdbcTemplate);
        UUID matchId = fixtures.match(teamA, teamB, "ARCHIVED", START, fixtures.venue(captainA.getId()));
        matches.add(matchId);
        jdbcTemplate.update("UPDATE team SET clean_streak = 4, report_count_noshow = 0 WHERE id = ?", teamB);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch penalized = new CountDownLatch(1);
        CountDownLatch releaseReport = new CountDownLatch(1);
        try {
            Future<?> report = executor.submit(() -> tx.executeWithoutResult(status -> {
                fixtures.report(matchId, teamA, teamB, "NOSHOW", "AUTO_ACCEPTED", 5, captainA.getId(),
                        START.plusHours(2));
                teamRepository.applyReportPenalty(teamB, "NOSHOW", ReportPenaltyPolicy.tiers(Report.ReportType.NOSHOW));
                penalized.countDown();
                awaitQuietly(releaseReport);
            }));
            assertThat(penalized.await(LOCK_WAIT_TIMEOUT_MS, MILLISECONDS)).isTrue();

            CompletableFuture<Integer> completionSession = new CompletableFuture<>();
            Future<?> completion = executor.submit(() -> tx.executeWithoutResult(status -> {
                // Cùng connection với lời gọi hoàn tất bên dưới: transaction gắn connection này vào thread hiện tại.
                completionSession.complete(jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Integer.class));
                reputationService.applyMatchCompletion(matchId,
                        List.of(new CompletionEntry(teamA, true, 0), new CompletionEntry(teamB, true, 0)));
            }));
            awaitBlockedOnLock(completionSession.get(LOCK_WAIT_TIMEOUT_MS, MILLISECONDS));
            releaseReport.countDown();

            report.get(30, SECONDS);
            completion.get(30, SECONDS);
            assertThat(count("SELECT clean_streak FROM team WHERE id = ?", teamB)).isZero();
            assertThat(count("SELECT report_count_noshow FROM team WHERE id = ?", teamB)).isEqualTo(1);
        } finally {
            releaseReport.countDown();
            executor.shutdownNow();
        }
    }

    /**
     * Ngược thứ tự của test trên: hoàn tất trận giữ khoá đội B trước (chưa thấy report chưa commit → trận sạch, chạm
     * mốc chuỗi 5: +2 +5 và bộ đếm report về 0); report bùng chờ khoá rồi trừ theo bộ đếm mới (lần 1 → 5). Kết quả bằng
     * thứ tự tuần tự "hoàn tất → report": 80 → 87 → 82, chuỗi 0, bộ đếm bùng 1.
     */
    @Test
    void reportWaitingOnCompletionIsAppliedAsReportAfterCompletion() throws Exception {
        User captainA = user("Completion First A");
        User captainB = user("Completion First B");
        UUID teamA = team(captainA);
        UUID teamB = team(captainB);
        ReputationDbFixtures fixtures = new ReputationDbFixtures(jdbcTemplate);
        UUID matchId = fixtures.match(teamA, teamB, "ARCHIVED", START, fixtures.venue(captainA.getId()));
        matches.add(matchId);
        jdbcTemplate.update("UPDATE team SET reliability_score = 80, clean_streak = 4, report_count_noshow = 2 WHERE id = ?",
                teamB);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch completed = new CountDownLatch(1);
        CountDownLatch releaseCompletion = new CountDownLatch(1);
        try {
            Future<?> completion = executor.submit(() -> tx.executeWithoutResult(status -> {
                reputationService.applyMatchCompletion(matchId,
                        List.of(new CompletionEntry(teamA, true, 0), new CompletionEntry(teamB, true, 0)));
                completed.countDown();
                awaitQuietly(releaseCompletion);
            }));
            assertThat(completed.await(LOCK_WAIT_TIMEOUT_MS, MILLISECONDS)).isTrue();

            CompletableFuture<Integer> reportSession = new CompletableFuture<>();
            Future<?> report = executor.submit(() -> tx.executeWithoutResult(status -> {
                // Cùng connection với câu trừ điểm bên dưới: transaction gắn connection này vào thread hiện tại.
                reportSession.complete(jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Integer.class));
                fixtures.report(matchId, teamA, teamB, "NOSHOW", "AUTO_ACCEPTED", 5, captainA.getId(),
                        START.plusHours(2));
                teamRepository.applyReportPenalty(teamB, "NOSHOW", ReportPenaltyPolicy.tiers(Report.ReportType.NOSHOW));
            }));
            awaitBlockedOnLock(reportSession.get(LOCK_WAIT_TIMEOUT_MS, MILLISECONDS));
            releaseCompletion.countDown();

            completion.get(30, SECONDS);
            report.get(30, SECONDS);
            assertThat(score(teamB)).isEqualTo(82);
            assertThat(count("SELECT clean_streak FROM team WHERE id = ?", teamB)).isZero();
            assertThat(count("SELECT report_count_noshow FROM team WHERE id = ?", teamB)).isEqualTo(1);
        } finally {
            releaseCompletion.countDown();
            executor.shutdownNow();
        }
    }

    private void awaitBlockedOnLock(int pid) throws InterruptedException {
        long deadline = System.currentTimeMillis() + LOCK_WAIT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (count("SELECT COUNT(*) FROM pg_stat_activity WHERE pid = ? AND wait_event_type = 'Lock'", pid) > 0) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("session " + pid + " never waited on a row lock");
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(30, SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private int score(UUID teamId) {
        Integer score = jdbcTemplate.queryForObject("SELECT reliability_score FROM team WHERE id = ?", Integer.class,
                teamId);
        return score == null ? 0 : score;
    }

    /** Chạy hai thao tác cùng lúc; trả kết quả hoặc exception của từng thao tác. */
    private static List<Object> race(Callable<?> first, Callable<?> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<?> call : List.of(first, second)) {
                futures.add(executor.submit(() -> {
                    start.await();
                    try {
                        return call.call();
                    } catch (Exception exception) {
                        return exception;
                    }
                }));
            }
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }
}
