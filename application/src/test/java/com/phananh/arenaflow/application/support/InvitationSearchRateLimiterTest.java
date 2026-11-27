package com.phananh.arenaflow.application.support;

import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.common.exception.RateLimitExceededException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvitationSearchRateLimiterTest {

    private static final long SECOND = Duration.ofSeconds(1).toNanos();

    /** Đồng hồ giả: bắt đầu ở một mốc tuỳ ý (nanoTime có thể âm). */
    private final AtomicLong now = new AtomicLong(-5_000 * SECOND);
    private final InvitationSearchRateLimiter limiter = new InvitationSearchRateLimiter(now::get);
    private final UUID user = UUID.randomUUID();

    private void advanceSeconds(double seconds) {
        now.addAndGet((long) (seconds * SECOND));
    }

    private void acquireTimes(UUID userId, int times) {
        for (int i = 0; i < times; i++) {
            limiter.acquire(userId);
        }
    }

    private RateLimitExceededException rejected(UUID userId) {
        try {
            limiter.acquire(userId);
        } catch (RateLimitExceededException exception) {
            return exception;
        }
        throw new AssertionError("Lần gọi phải bị từ chối");
    }

    @Test
    void acceptsThirtyCallsThenRejectsThirtyFirstWithRetryAfterUntilOldestCallLeavesWindow() {
        limiter.acquire(user);               // t = 0
        advanceSeconds(10);
        acquireTimes(user, 29);              // t = 10 → đủ 30 lần

        RateLimitExceededException exception = rejected(user);

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_007);
        assertThat(exception.getRetryAfterSeconds()).isEqualTo(50);
    }

    @Test
    void retryAfterIsRoundedUpAndAtLeastOneSecond() {
        acquireTimes(user, 30);
        advanceSeconds(59.9);
        // Lần cũ nhất ra khỏi cửa sổ sau 0,1 giây → làm tròn lên 1.
        assertThat(rejected(user).getRetryAfterSeconds()).isEqualTo(1);

        advanceSeconds(-10);
        assertThat(rejected(user).getRetryAfterSeconds()).isEqualTo(11);
    }

    @Test
    void rejectedCallsAreNotRecordedAndDoNotPushBackReopening() {
        acquireTimes(user, 30);              // t = 0
        advanceSeconds(30);
        for (int i = 0; i < 10; i++) {
            rejected(user);                  // t = 30, bị từ chối, không ghi mốc
        }
        advanceSeconds(30.000001);           // t = 60 + ε: cả 30 mốc ở t = 0 đã ra khỏi cửa sổ

        assertThatCode(() -> acquireTimes(user, 30)).doesNotThrowAnyException();
        assertThat(rejected(user).getErrorCode()).isEqualTo(ErrorCode.USER_007);
    }

    @Test
    void slidingWindowReleasesOnlyCallsOlderThanSixtySeconds() {
        acquireTimes(user, 15);              // t = 0
        advanceSeconds(30);
        acquireTimes(user, 15);              // t = 30
        advanceSeconds(30);                  // t = 60: mốc t = 0 đúng biên → đã hết hạn

        acquireTimes(user, 15);
        RateLimitExceededException exception = rejected(user);
        // Lần cũ nhất còn lại ở t = 30 → ra khỏi cửa sổ lúc t = 90.
        assertThat(exception.getRetryAfterSeconds()).isEqualTo(30);
    }

    @Test
    void usersAreLimitedIndependently() {
        UUID other = UUID.randomUUID();
        acquireTimes(user, 30);

        assertThatCode(() -> acquireTimes(other, 30)).doesNotThrowAnyException();
        assertThat(rejected(user).getErrorCode()).isEqualTo(ErrorCode.USER_007);
        assertThat(rejected(other).getErrorCode()).isEqualTo(ErrorCode.USER_007);
    }

    @Test
    void idleUsersAreSweptFromMemoryLazily() {
        UUID idle = UUID.randomUUID();
        limiter.acquire(idle);
        assertThat(limiter.trackedUsers()).isEqualTo(1);

        advanceSeconds(121);
        limiter.acquire(user);

        assertThat(limiter.trackedUsers()).isEqualTo(1);
        assertThatCode(() -> acquireTimes(idle, 30)).doesNotThrowAnyException();
    }

    @Test
    void sweepRunsAtMostOncePerWindowAndKeepsActiveUsers() {
        UUID idle = UUID.randomUUID();
        limiter.acquire(user);
        limiter.acquire(idle);
        advanceSeconds(61);
        limiter.acquire(user);               // lượt dọn: idle hết mốc → bị xoá; user còn mốc mới
        assertThat(limiter.trackedUsers()).isEqualTo(1);

        limiter.acquire(idle);
        advanceSeconds(10);
        limiter.acquire(user);               // chưa tới lượt dọn kế tiếp
        assertThat(limiter.trackedUsers()).isEqualTo(2);
    }

    @Test
    void concurrentCallsOfSameUserAcceptExactlyThirty() throws Exception {
        InvitationSearchRateLimiter realClock = new InvitationSearchRateLimiter();
        int threads = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    try {
                        realClock.acquire(user);
                        return true;
                    } catch (RateLimitExceededException exception) {
                        return false;
                    }
                }));
            }
            start.countDown();
            int accepted = 0;
            for (Future<Boolean> result : results) {
                if (result.get(10, TimeUnit.SECONDS)) {
                    accepted++;
                }
            }
            assertThat(accepted).isEqualTo(30);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void nullUserIsRejectedAsProgrammingError() {
        assertThatThrownBy(() -> limiter.acquire(null)).isInstanceOf(NullPointerException.class);
    }
}
