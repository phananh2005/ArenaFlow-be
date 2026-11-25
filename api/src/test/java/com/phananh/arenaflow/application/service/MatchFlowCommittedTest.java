package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.SendMatchRequestRequest;
import com.phananh.arenaflow.application.dto.response.MatchRequestResponse;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.common.time.AppTime;
import com.phananh.arenaflow.domain.entity.user.User;
import com.phananh.arenaflow.domain.repository.MatchRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Kèo luồng A với commit thật (DB dev): race chấp nhận, nhánh chuyển EXPIRED được commit rồi mới báo lỗi, job claim
 * {@code SKIP LOCKED}. Dữ liệu kèo / yêu cầu / cuộc trò chuyện / địa điểm tạo ra được xoá sau mỗi test.
 */
class MatchFlowCommittedTest extends CommittedDataIntegrationTest {

    @Autowired private MatchRequestService matchRequestService;
    @Autowired private MatchLifecycleJobService jobService;
    @Autowired private MatchRepository matchRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    private final List<UUID> createdMatches = new ArrayList<>();
    private final List<UUID> createdVenues = new ArrayList<>();

    @AfterEach
    void deleteMatches() {
        for (UUID matchId : createdMatches) {
            jdbcTemplate.update("DELETE FROM conversation WHERE match_id = ?", matchId);
            jdbcTemplate.update("DELETE FROM match_request WHERE match_id = ?", matchId);
            jdbcTemplate.update("DELETE FROM match WHERE id = ?", matchId);
        }
        createdVenues.forEach(id -> jdbcTemplate.update("DELETE FROM venue WHERE id = ?", id));
    }

    private UUID insertMatch(UUID authorTeam, User owner, LocalDateTime expiresAt) {
        UUID venueId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO venue (id, name, address, latitude, longitude, created_by_type, created_by_user_id) "
                + "VALUES (?, 'Sân race', 'Địa chỉ', 10.7, 106.7, 'USER', ?)", venueId, owner.getId());
        createdVenues.add(venueId);
        UUID sportId = jdbcTemplate.queryForObject("SELECT id FROM sport WHERE is_active ORDER BY name LIMIT 1", UUID.class);
        UUID matchId = UUID.randomUUID();
        LocalDateTime start = AppTime.now().plusDays(1);
        jdbcTemplate.update("INSERT INTO match (id, type, author_team_id, status, sport_id, format, venue_id, "
                + "match_start_time, match_end_time, expires_at) VALUES (?, 'HAS_VENUE', ?, 'OPEN', ?, 'FIVE_A_SIDE', ?, ?, ?, ?)",
                matchId, authorTeam, sportId, venueId, start, start.plusHours(2), expiresAt);
        createdMatches.add(matchId);
        return matchId;
    }

    @Test
    void twoConcurrentAcceptsConfirmExactlyOneRequest() throws Exception {
        User author = user("Race tác giả");
        UUID authorTeam = team(author);
        User x = user("Race X");
        User y = user("Race Y");
        UUID xTeam = team(x);
        UUID yTeam = team(y);
        UUID matchId = insertMatch(authorTeam, author, AppTime.now().plusHours(5));
        MatchRequestResponse rx = matchRequestService.sendRequest(matchId, x.getId(), new SendMatchRequestRequest(xTeam));
        MatchRequestResponse ry = matchRequestService.sendRequest(matchId, y.getId(), new SendMatchRequestRequest(yTeam));

        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<ErrorCode>> calls = List.of(
                    () -> acceptAfter(go, rx.requestId(), author),
                    () -> acceptAfter(go, ry.requestId(), author));
            List<Future<ErrorCode>> results = calls.stream().map(pool::submit).toList();
            go.countDown();
            List<ErrorCode> outcomes = new ArrayList<>();
            for (Future<ErrorCode> result : results) {
                outcomes.add(result.get(30, TimeUnit.SECONDS));
            }
            assertThat(outcomes).containsExactlyInAnyOrder(null, ErrorCode.MATCH_003);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM match_request WHERE match_id = ? AND status = 'ACCEPTED'",
                Integer.class, matchId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM match_request WHERE match_id = ? AND status = 'PENDING'",
                Integer.class, matchId)).isZero();
    }

    private ErrorCode acceptAfter(CountDownLatch go, UUID requestId, User author) throws InterruptedException {
        go.await();
        try {
            matchRequestService.acceptRequest(requestId, author.getId());
            return null;
        } catch (BusinessException exception) {
            return exception.getErrorCode();
        }
    }

    @Test
    void sendingRequestToOverdueOpenMatchCommitsExpiryThenFailsWithMatch004() {
        User author = user("Hết hạn tác giả");
        UUID authorTeam = team(author);
        User x = user("Hết hạn X");
        UUID xTeam = team(x);
        UUID matchId = insertMatch(authorTeam, author, AppTime.now().minusMinutes(1));

        assertThatThrownBy(() -> matchRequestService.sendRequest(matchId, x.getId(), new SendMatchRequestRequest(xTeam)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MATCH_004));
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM match WHERE id = ?", String.class, matchId))
                .isEqualTo("EXPIRED");
    }

    @Test
    void jobSkipsRowLockedByAnotherTransactionAndExpiresTheRest() throws Exception {
        User author = user("Job tác giả");
        UUID authorTeam = team(author);
        UUID locked = insertMatch(authorTeam, author, AppTime.now().minusMinutes(5));
        UUID free = insertMatch(authorTeam, author, AppTime.now().minusMinutes(4));
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> holder = pool.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                jdbcTemplate.queryForObject("SELECT id FROM match WHERE id = ? FOR UPDATE", UUID.class, locked);
                lockHeld.countDown();
                try {
                    release.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(lockHeld.await(30, TimeUnit.SECONDS)).isTrue();

            jobService.expireDueMatches();

            assertThat(jdbcTemplate.queryForObject("SELECT status FROM match WHERE id = ?", String.class, free))
                    .isEqualTo("EXPIRED");
            assertThat(jdbcTemplate.queryForObject("SELECT status FROM match WHERE id = ?", String.class, locked))
                    .isEqualTo("OPEN");
            release.countDown();
            holder.get(30, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
        assertThat(matchRepository.findById(locked)).isPresent();
    }
}
