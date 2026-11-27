package com.phananh.arenaflow.application.listener;

import com.phananh.arenaflow.application.event.user.UserRegisteredEvent;
import com.phananh.arenaflow.domain.entity.user.User;
import com.phananh.arenaflow.domain.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Listener bất đồng bộ sau commit: commit → đúng một thông báo (trên luồng executor riêng); rollback → không có.
 * Không bọc @Transactional vì cần commit thật. Chạy ở giai đoạn tích hợp (DB có V6–V11).
 */
@SpringBootTest
class NotificationListenerIntegrationTest {

    @Autowired private ApplicationEventPublisher eventPublisher;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private Clock clock;

    private UUID userId;

    @AfterEach
    void cleanUp() {
        if (userId != null) {
            jdbcTemplate.update("DELETE FROM notification WHERE recipient_user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM \"user\" WHERE id = ?", userId);
        }
    }

    @Test
    void committedEventCreatesExactlyOneNotification() throws InterruptedException {
        userId = transactionTemplate.execute(status -> {
            UUID id = userRepository.save(User.builder().displayName("Async Listener").build()).getId();
            eventPublisher.publishEvent(new UserRegisteredEvent(id, LocalDateTime.now(clock)));
            return id;
        });

        assertThat(awaitNotifications(1)).isEqualTo(1);
        Thread.sleep(500);
        assertThat(countNotifications()).isEqualTo(1);
    }

    @Test
    void rolledBackEventCreatesNothing() throws InterruptedException {
        userId = transactionTemplate.execute(status ->
                userRepository.save(User.builder().displayName("Async Rollback").build()).getId());

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(new UserRegisteredEvent(userId, LocalDateTime.now(clock)));
            status.setRollbackOnly();
        });

        Thread.sleep(1_000);
        assertThat(countNotifications()).isZero();
    }

    private int awaitNotifications(int expected) throws InterruptedException {
        for (int attempt = 0; attempt < 50; attempt++) {
            if (countNotifications() >= expected) {
                break;
            }
            Thread.sleep(100);
        }
        return countNotifications();
    }

    private int countNotifications() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notification WHERE recipient_user_id = ?",
                Integer.class, userId);
        return count == null ? 0 : count;
    }
}
