package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.support.ConcurrencyConflictRetrier;
import com.phananh.arenaflow.domain.entity.team.TeamMember;
import com.phananh.arenaflow.domain.entity.user.User;
import com.phananh.arenaflow.domain.repository.TeamMemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deadlock thật trên PostgreSQL: hai transaction khoá membership của hai user theo thứ tự ngược nhau (cùng query khoá
 * của xoá tài khoản). PostgreSQL huỷ một bên ({@code 40P01}).
 */
class RetrierDeadlockIntegrationTest extends CommittedDataIntegrationTest {

    private static final String DEADLOCK_DETECTED = "40P01";
    private static final List<TeamMember.Status> OPEN = List.of(TeamMember.Status.ACTIVE, TeamMember.Status.PENDING);

    @Autowired private TeamMemberRepository teamMemberRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private ConcurrencyConflictRetrier retrier;

    @Test
    void deadlockVictimSurfacesAsPessimisticLockingFailure() throws Exception {
        User x = user("Deadlock X");
        User y = user("Deadlock Y");
        team(x);
        team(y);
        assertOneOpenMembershipEach(x, y);
        CyclicBarrier bothHoldFirstLock = new CyclicBarrier(2);

        List<Throwable> failures = runConcurrently(
                () -> transactionTemplate.execute(status -> lockBoth(x, y, bothHoldFirstLock, null)),
                () -> transactionTemplate.execute(status -> lockBoth(y, x, bothHoldFirstLock, null)));

        assertThat(failures).hasSize(1);
        assertThat(failures.get(0)).isInstanceOf(CannotAcquireLockException.class);
        assertThat(sqlState(failures.get(0))).isEqualTo(DEADLOCK_DETECTED);
    }

    @Test
    void retrierRetriesDeadlockVictimSoBothOperationsSucceed() throws Exception {
        User x = user("Retry Deadlock X");
        User y = user("Retry Deadlock Y");
        team(x);
        team(y);
        assertOneOpenMembershipEach(x, y);
        CyclicBarrier bothHoldFirstLock = new CyclicBarrier(2);
        AtomicInteger attempts = new AtomicInteger();

        List<Throwable> failures = runConcurrently(
                () -> retrier.execute(() -> lockBoth(x, y, bothHoldFirstLock, attempts)),
                () -> retrier.execute(() -> lockBoth(y, x, bothHoldFirstLock, attempts)));

        assertThat(failures).isEmpty();
        assertThat(attempts).hasValue(3); // 2 lần thử đầu + 1 lần thử lại của bên bị huỷ
    }

    /** Mỗi user có đúng 1 membership mở để bị khoá — nếu không sẽ không có deadlock, test fail khó chẩn đoán. */
    private void assertOneOpenMembershipEach(User... users) {
        for (User user : users) {
            assertThat(count("SELECT COUNT(*) FROM team_member WHERE user_id = ? AND status IN ('ACTIVE', 'PENDING')",
                    user.getId())).as("open memberships of %s", user.getDisplayName()).isEqualTo(1);
        }
    }

    /** Khoá membership của {@code first}, đợi bên kia giữ khoá đầu (chỉ 2 lần thử đầu), rồi khoá {@code second}. */
    private int lockBoth(User first, User second, CyclicBarrier bothHoldFirstLock, AtomicInteger attempts) {
        int attempt = attempts == null ? 1 : attempts.incrementAndGet();
        int rows = teamMemberRepository.findByUserIdAndStatusInOrderByIdAsc(first.getId(), OPEN).size();
        if (attempt <= 2) {
            await(bothHoldFirstLock);
        }
        return rows + teamMemberRepository.findByUserIdAndStatusInOrderByIdAsc(second.getId(), OPEN).size();
    }

    private static List<Throwable> runConcurrently(Supplier<?> first, Supplier<?> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> futures = List.of(executor.submit(first::get), executor.submit(second::get));
            List<Throwable> failures = new ArrayList<>();
            for (Future<?> future : futures) {
                try {
                    future.get(60, SECONDS);
                } catch (ExecutionException exception) {
                    failures.add(exception.getCause());
                } catch (TimeoutException exception) {
                    throw new AssertionError("Luồng không kết thúc trong 60s (khoá DB bị kẹt?)", exception);
                }
            }
            return failures;
        } finally {
            executor.shutdownNow();
        }
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(30, SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("Barrier không đồng bộ được hai luồng", exception);
        }
    }
}
