package com.phananh.arenaflow.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Hợp đồng hồi quy cho Flyway V11. Chỉ chạy được trên DB đã có V6–V10 (giai đoạn tích hợp), cấu hình qua .env.dev.
 */
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=none")
class NotificationMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void appliesNotificationSchema() {
        assertThat(constraintExists("notification_type_check")).isTrue();
        assertThat(constraintExists("notification_reference_type_check")).isTrue();
        assertThat(constraintExists("notification_reference_check")).isTrue();
        assertThat(indexExists("idx_notification_recipient")).isFalse();
        assertThat(indexExists("idx_notification_recipient_time")).isTrue();
        assertThat(indexExists("idx_notification_recipient_unread")).isTrue();
        assertThat(indexExists("idx_notification_retention")).isTrue();
        assertThat(indexExists("idx_match_reminder_match")).isTrue();
        assertThat(indexExists("idx_match_reminder_external")).isTrue();
        assertThat(indexExists("idx_match_reminder_sent")).isTrue();
        assertThat(indexExists("idx_match_confirmed_start")).isTrue();
        assertThat(indexExists("idx_external_match_scheduled_start")).isTrue();
        assertThat(indexExists("idx_external_match_scheduled_end")).isTrue();
        assertThat(indexExists("idx_user_active_admin")).isTrue();
    }

    @Test
    @Transactional
    void acceptsNewTypeAndRejectsUnknownTypeOrHalfReference() {
        UUID user = insertUser();

        insertNotification(user, "VENUE_ADMIN_ALERT", "VENUE", UUID.randomUUID());
        insertNotification(user, "WELCOME", null, null);

        assertThatThrownBy(() -> insertNotification(user, "UNKNOWN_TYPE", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertNotification(user, "WELCOME", "USER", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertNotification(user, "WELCOME", "SOMETHING", UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void matchReminderNeedsExactlyOneGame() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO match_reminder (id, kind, match_id, external_match_id, sent_at)
                VALUES (?, 'BEFORE_START', NULL, NULL, (now() AT TIME ZONE 'Asia/Ho_Chi_Minh'))
                """, UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID insertUser() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO \"user\" (id, display_name) VALUES (?, 'Migration User')", id);
        return id;
    }

    private void insertNotification(UUID recipient, String type, String referenceType, UUID referenceId) {
        jdbcTemplate.update("""
                INSERT INTO notification (id, recipient_user_id, type, title, body, reference_type, reference_id)
                VALUES (?, ?, ?, 't', 'b', ?, ?)
                """, UUID.randomUUID(), recipient, type, referenceType, referenceId);
    }

    private boolean constraintExists(String name) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = ?)", Boolean.class, name));
    }

    private boolean indexExists(String name) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = ?)", Boolean.class, name));
    }
}
