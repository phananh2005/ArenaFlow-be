package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.domain.entity.team.TeamMember;
import com.phananh.arenaflow.domain.entity.user.User;
import com.phananh.arenaflow.domain.repository.TeamMemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ADR-0007: bước khoá của xoá tài khoản ({@code findByUserIdAndStatusInOrderByIdAsc}) phải giữ khoá dòng thật đến hết
 * transaction — kết nối khác không lấy được khoá cùng row ({@code FOR UPDATE NOWAIT} → SQLState 55P03) cho tới khi commit.
 */
class AccountDeletionLockIntegrationTest extends CommittedDataIntegrationTest {

    private static final String LOCK_NOT_AVAILABLE = "55P03";
    private static final String TRY_LOCK = "SELECT id FROM team_member WHERE id = ? FOR UPDATE NOWAIT";

    @Autowired private TeamMemberRepository teamMemberRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    @Test
    void lockingOpenMembershipsBlocksOtherLockersUntilTransactionEnds() throws Exception {
        User captain = user("Lock Captain");
        User member = user("Lock Member");
        UUID teamId = team(captain);
        join(teamId, captain, member);
        UUID rowId = jdbcTemplate.queryForObject("SELECT id FROM team_member WHERE team_id = ? AND user_id = ?",
                UUID.class, teamId, member.getId());
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Integer> holder = executor.submit(() -> transactionTemplate.execute(status -> {
                int rows = teamMemberRepository.findByUserIdAndStatusInOrderByIdAsc(member.getId(),
                        List.of(TeamMember.Status.ACTIVE, TeamMember.Status.PENDING)).size();
                locked.countDown();
                awaitQuietly(release);
                return rows;
            }));
            assertThat(locked.await(30, SECONDS)).isTrue();

            assertThatThrownBy(() -> jdbcTemplate.queryForList(TRY_LOCK, rowId))
                    .isInstanceOf(DataAccessException.class)
                    .satisfies(exception -> assertThat(sqlState((DataAccessException) exception))
                            .isEqualTo(LOCK_NOT_AVAILABLE));

            release.countDown();
            assertThat(holder.get(30, SECONDS)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForList(TRY_LOCK, rowId)).hasSize(1); // đã nhả khoá sau commit
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private static String sqlState(DataAccessException exception) {
        return exception.getMostSpecificCause() instanceof SQLException sql ? sql.getSQLState() : null;
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(30, SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
