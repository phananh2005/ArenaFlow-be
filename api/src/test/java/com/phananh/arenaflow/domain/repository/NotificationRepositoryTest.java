package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.entity.user.User;
import com.phananh.arenaflow.domain.projection.InsertedNotificationView;
import com.phananh.arenaflow.domain.projection.NotificationView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Chạy ở giai đoạn tích hợp (DB có V6–V11): bind mảng, RETURNING không @Modifying, con trỏ, đánh dấu đọc. */
class NotificationRepositoryTest extends RepositoryIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 21, 10, 0);
    private static final LocalDateTime VISIBLE_FROM = NOW.minusDays(30);

    @Autowired
    private NotificationRepository repository;
    @Autowired
    private UserRepository userRepository;

    private UUID recipient;

    @BeforeEach
    void setUp() {
        recipient = userRepository.save(User.builder().displayName("Notified").build()).getId();
        flushAndClear();
    }

    @Test
    void insertAllReturningWritesBatchInOneStatementAndSkipsDeletedRecipients() {
        UUID deleted = userRepository.save(User.builder().displayName("Gone").build()).getId();
        entityManager.flush();
        entityManager.createNativeQuery("UPDATE \"user\" SET deleted_at = (now() AT TIME ZONE 'Asia/Ho_Chi_Minh') "
                + "WHERE id = :id").setParameter("id", deleted).executeUpdate();
        flushAndClear();
        var statistics = resetStatistics();

        List<InsertedNotificationView> rows = repository.insertAllReturning(new UUID[]{recipient, deleted},
                new String[]{"WELCOME", "WELCOME"}, new String[]{"t1", "t2"}, new String[]{"b1", "b2"},
                new String[]{"USER", null}, new UUID[]{recipient, null}, NOW);

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.getRecipientUserId()).isEqualTo(recipient);
            assertThat(row.getReferenceType()).isEqualTo("USER");
            assertThat(row.getCreatedAt()).isEqualTo(NOW);
        });
    }

    @Test
    void cursorPagesAreStableAndVisibleWindowHidesOldRows() {
        insert(NOW.minusDays(31), "old");
        UUID first = insert(NOW.minusHours(3), "first");
        UUID second = insert(NOW.minusHours(2), "second");
        UUID third = insert(NOW.minusHours(1), "third");
        flushAndClear();

        assertThat(repository.findLatest(recipient, VISIBLE_FROM, 10)).extracting(NotificationView::getId)
                .containsExactly(third, second, first);
        assertThat(repository.findBefore(recipient, VISIBLE_FROM, NOW.minusHours(2), second, 10))
                .extracting(NotificationView::getId).containsExactly(first);
        assertThat(repository.findAfter(recipient, VISIBLE_FROM, NOW.minusHours(2), second, 10))
                .extracting(NotificationView::getId).containsExactly(third);
        assertThat(repository.findCaller(recipient, second, VISIBLE_FROM)).get()
                .satisfies(view -> assertThat(view.getAnchorCreatedAt()).isEqualTo(NOW.minusHours(2)));
        assertThat(repository.findCaller(recipient, UUID.randomUUID(), VISIBLE_FROM)).get()
                .satisfies(view -> assertThat(view.getAnchorCreatedAt()).isNull());
    }

    @Test
    void markReadAndCountUnread() {
        UUID first = insert(NOW.minusHours(2), "first");
        insert(NOW.minusHours(1), "second");
        flushAndClear();

        assertThat(repository.countUnread(recipient, VISIBLE_FROM)).get()
                .satisfies(view -> assertThat(view.getUnreadCount()).isEqualTo(2));
        assertThat(repository.markRead(UUID.randomUUID(), first)).isZero();
        assertThat(repository.markRead(recipient, first)).isEqualTo(1);
        assertThat(repository.markRead(recipient, first)).isZero();
        assertThat(repository.findReadContext(recipient, first, VISIBLE_FROM)).get()
                .satisfies(view -> {
                    assertThat(view.getFound()).isTrue();
                    assertThat(view.getIsRead()).isTrue();
                });
        assertThat(repository.findLatestUnread(recipient, VISIBLE_FROM, 10)).hasSize(1);
        assertThat(repository.markAllRead(recipient, VISIBLE_FROM)).isEqualTo(1);
        assertThat(repository.countUnread(recipient, VISIBLE_FROM)).get()
                .satisfies(view -> assertThat(view.getUnreadCount()).isZero());
    }

    private UUID insert(LocalDateTime createdAt, String title) {
        return repository.insertAllReturning(new UUID[]{recipient}, new String[]{"WELCOME"}, new String[]{title},
                new String[]{"b"}, new String[]{null}, new UUID[]{null}, createdAt).getFirst().getId();
    }
}
