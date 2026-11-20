package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.domain.projection.StaleVenueView;
import com.phananh.arenaflow.domain.repository.VenueRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lệnh claim cảnh báo địa điểm lâu không được chọn phải chờ một dòng venue mà transaction khác đang sửa; khi
 * transaction đó commit, claim phải kiểm lại dòng mới và bỏ qua nó. Fixture đặt mốc năm 1990 với ngưỡng năm 2000
 * nên claim đã commit không chạm địa điểm khác trong database dev.
 */
class VenueStaleAlertRaceTest extends CommittedDataIntegrationTest {

    private static final OffsetDateTime THRESHOLD = OffsetDateTime.parse("2000-01-01T00:00:00Z");
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-20T03:00:00Z");
    private static final int LARGE_LIMIT = 100_000;
    private static final long LOCK_WAIT_TIMEOUT_MS = 10_000;

    @Autowired private VenueRepository venueRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private final List<UUID> createdVenues = new ArrayList<>();
    private ExecutorService executor;

    @BeforeEach
    void startExecutor() {
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void deleteVenues() {
        executor.shutdownNow();
        createdVenues.forEach(venueId -> jdbcTemplate.update("DELETE FROM venue WHERE id = ?", venueId));
    }

    @Test
    void claimSkipsVenueSelectedWhileClaimWaited() throws Exception {
        UUID venueId = staleVenue();

        List<UUID> claimed = claimWhileConcurrentWriteCommits(venueId,
                id -> venueRepository.markSelected(List.of(id), NOW));

        assertThat(claimed).doesNotContain(venueId);
        assertThat(count("SELECT COUNT(*) FROM venue WHERE id = ? AND stale_alerted_at IS NULL", venueId)).isEqualTo(1);
    }

    @Test
    void claimSkipsVenueMarkedInactiveWhileClaimWaited() throws Exception {
        UUID venueId = staleVenue();

        List<UUID> claimed = claimWhileConcurrentWriteCommits(venueId, id -> jdbcTemplate.update(
                "UPDATE venue SET status = 'INACTIVE', admin_verified_inactive_at = now(), version = version + 1 "
                        + "WHERE id = ?", id));

        assertThat(claimed).doesNotContain(venueId);
        assertThat(count("SELECT COUNT(*) FROM venue WHERE id = ? AND stale_alerted_at IS NULL", venueId)).isEqualTo(1);
    }

    /**
     * Writer cập nhật dòng và giữ khoá; claim bắt đầu, chụp snapshot dòng cũ rồi bị chặn ở khoá đó; writer chỉ
     * commit sau khi thấy chính session database của claim này đang chờ khoá.
     */
    private List<UUID> claimWhileConcurrentWriteCommits(UUID venueId, Consumer<UUID> write) throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        CountDownLatch written = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> writer = executor.submit(() -> tx.executeWithoutResult(status -> {
            write.accept(venueId);
            written.countDown();
            await(release);
        }));
        assertThat(written.await(LOCK_WAIT_TIMEOUT_MS, MILLISECONDS)).isTrue();

        CompletableFuture<Integer> claimSession = new CompletableFuture<>();
        Future<List<UUID>> claim = executor.submit(() -> tx.execute(status -> {
            // Cùng connection với claim bên dưới: transaction gắn connection này vào thread hiện tại.
            claimSession.complete(jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Integer.class));
            return venueRepository.markStaleAlerted(THRESHOLD, NOW, LARGE_LIMIT).stream()
                    .map(StaleVenueView::getId)
                    .filter(createdVenues::contains)
                    .toList();
        }));
        awaitBlockedOnLock(claimSession.get(LOCK_WAIT_TIMEOUT_MS, MILLISECONDS));
        release.countDown();

        writer.get(30, SECONDS);
        return claim.get(30, SECONDS);
    }

    private void awaitBlockedOnLock(int pid) throws InterruptedException {
        long deadline = System.currentTimeMillis() + LOCK_WAIT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (count("SELECT COUNT(*) FROM pg_stat_activity WHERE pid = ? AND wait_event_type = 'Lock'", pid) > 0) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("stale-alert claim session " + pid + " never waited on the venue row lock");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(30, SECONDS)) {
                throw new IllegalStateException("writer was never released");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    /** Địa điểm ACTIVE tạo năm 1990, chưa từng được chọn, chưa cảnh báo: đủ điều kiện với ngưỡng năm 2000. */
    private UUID staleVenue() {
        UUID creator = user("Stale Race Creator").getId();
        OffsetDateTime createdAt = OffsetDateTime.parse("1990-01-01T00:00:00Z");
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO venue (id, name, address, latitude, longitude, status, created_by_type, "
                        + "created_by_user_id, created_at, updated_at) "
                        + "VALUES (?, 'Stale Race Venue', 'Address', 10.1, 106.1, 'ACTIVE', 'USER', ?, ?, ?)",
                id, creator, createdAt, createdAt);
        createdVenues.add(id);
        return id;
    }
}
