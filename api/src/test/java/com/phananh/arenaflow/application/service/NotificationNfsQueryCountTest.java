package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.NotificationListQuery;
import com.phananh.arenaflow.domain.entity.user.User;
import com.phananh.arenaflow.domain.repository.NotificationRepository;
import com.phananh.arenaflow.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Đếm số câu SQL thật của 2.1–2.4 so với ngưỡng (đọc chi tiết 3, danh sách 4, ghi 6). Chạy ở giai đoạn tích hợp. */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class NotificationNfsQueryCountTest {

    @Autowired private NotificationService notificationService;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private Clock clock;

    private Statistics statistics;
    private UUID caller;
    private UUID anchor;

    @BeforeEach
    void setUp() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        caller = userRepository.save(User.builder().displayName("NFS Notified").build()).getId();
        entityManager.flush();
        LocalDateTime now = LocalDateTime.now(clock);
        anchor = notificationRepository.insertAllReturning(new UUID[]{caller, caller}, new String[]{"WELCOME", "WELCOME"},
                new String[]{"t1", "t2"}, new String[]{"b1", "b2"}, new String[]{null, null}, new UUID[]{null, null},
                now).getFirst().getId();
    }

    private long measure(Runnable call) {
        entityManager.flush();
        entityManager.clear();
        statistics.clear();
        call.run();
        entityManager.flush();
        return statistics.getPrepareStatementCount();
    }

    @Test
    void endpointsStayWithinLimits() {
        assertThat(measure(() -> notificationService.listMine(caller, new NotificationListQuery(false, anchor, null, 10))))
                .as("GET /api/v1/profile/notifications").isEqualTo(2);
        assertThat(measure(() -> notificationService.countUnread(caller)))
                .as("GET /api/v1/profile/notifications/unread-count").isEqualTo(1);
        assertThat(measure(() -> notificationService.markRead(caller, anchor)))
                .as("POST /api/v1/profile/notifications/{id}/read").isEqualTo(2);
        assertThat(measure(() -> notificationService.markRead(caller, anchor)))
                .as("POST /api/v1/profile/notifications/{id}/read (đã đọc)").isEqualTo(1);
        assertThat(measure(() -> notificationService.markAllRead(caller)))
                .as("POST /api/v1/profile/notifications/read").isEqualTo(2);
    }
}
