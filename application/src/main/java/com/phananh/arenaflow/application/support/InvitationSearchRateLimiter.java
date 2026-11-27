package com.phananh.arenaflow.application.support;

import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.common.exception.RateLimitExceededException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Giới hạn tìm người để mời: tối đa {@link #MAX_CALLS} lần trong cửa sổ trượt {@link #WINDOW} gần nhất cho mỗi
 * người dùng, tính chung mọi đội và mọi nhánh tìm. Đếm trong bộ nhớ của instance (mất khi khởi động lại, không chia
 * sẻ giữa nhiều instance), không truy vấn DB. Dùng đồng hồ đơn điệu {@code nanoTime} nên không lệch khi đổi giờ máy.
 */
@Component
public class InvitationSearchRateLimiter {

    static final int MAX_CALLS = 30;
    static final Duration WINDOW = Duration.ofSeconds(60);

    private static final long WINDOW_NANOS = WINDOW.toNanos();
    private static final long SECOND_NANOS = Duration.ofSeconds(1).toNanos();

    /** Mốc nanoTime của các lần được chấp nhận, cũ nhất ở đầu; mỗi user tối đa {@link #MAX_CALLS} mốc. */
    private final ConcurrentHashMap<UUID, ArrayDeque<Long>> callsByUser = new ConcurrentHashMap<>();
    private final AtomicLong nextSweepAt;
    private final LongSupplier nanoTime;

    public InvitationSearchRateLimiter() {
        this(System::nanoTime);
    }

    /** Test truyền đồng hồ giả. */
    InvitationSearchRateLimiter(LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
        this.nextSweepAt = new AtomicLong(nanoTime.getAsLong() + WINDOW_NANOS);
    }

    /**
     * Ghi nhận một lần tìm của {@code userId}. Đã đủ {@link #MAX_CALLS} lần trong {@link #WINDOW} gần nhất → ném
     * {@link RateLimitExceededException} (USER_007) với số giây (làm tròn lên, tối thiểu 1) tới khi lần cũ nhất ra
     * khỏi cửa sổ; lần bị từ chối không được ghi nhận nên không làm lùi thời điểm gọi lại được.
     */
    public void acquire(UUID userId) {
        Objects.requireNonNull(userId, "userId");
        long now = nanoTime.getAsLong();
        sweepIfDue(now);
        boolean[] rejected = {false};
        long[] retryAfterNanos = {0};
        // compute nguyên tử theo key: các lần gọi đồng thời của cùng user được xử lý tuần tự.
        callsByUser.compute(userId, (id, calls) -> {
            ArrayDeque<Long> window = calls == null ? new ArrayDeque<>() : calls;
            dropExpired(window, now);
            if (window.size() >= MAX_CALLS) {
                rejected[0] = true;
                retryAfterNanos[0] = window.peekFirst() + WINDOW_NANOS - now;
            } else {
                window.addLast(now);
            }
            return window.isEmpty() ? null : window;
        });
        // Ném sau compute: không ném exception từ trong hàm remapping.
        if (rejected[0]) {
            throw new RateLimitExceededException(ErrorCode.USER_007, toRetryAfterSeconds(retryAfterNanos[0]));
        }
    }

    /** Số user đang được theo dõi — chỉ để test kiểm dọn bộ nhớ. */
    int trackedUsers() {
        return callsByUser.size();
    }

    /**
     * Dọn lười, tối đa một lượt mỗi {@link #WINDOW}: chỉ luồng thắng compareAndSet mới duyệt map; bỏ mốc hết hạn và
     * xoá user không còn mốc nào, nên map chỉ giữ user đã gọi trong khoảng hai cửa sổ gần nhất.
     */
    private void sweepIfDue(long now) {
        long due = nextSweepAt.get();
        if (now - due < 0 || !nextSweepAt.compareAndSet(due, now + WINDOW_NANOS)) {
            return;
        }
        for (UUID userId : callsByUser.keySet()) {
            callsByUser.computeIfPresent(userId, (id, calls) -> {
                dropExpired(calls, now);
                return calls.isEmpty() ? null : calls;
            });
        }
    }

    /** Mốc ở đúng biên {@code now - WINDOW} đã hết hạn (cửa sổ là khoảng nửa mở). So bằng hiệu để chịu tràn số. */
    private static void dropExpired(ArrayDeque<Long> calls, long now) {
        while (!calls.isEmpty() && now - calls.peekFirst() >= WINDOW_NANOS) {
            calls.pollFirst();
        }
    }

    private static long toRetryAfterSeconds(long nanos) {
        long seconds = (nanos + SECOND_NANOS - 1) / SECOND_NANOS;
        return Math.max(1, seconds);
    }
}
