package com.phananh.arenaflow.api.notification;

import com.phananh.arenaflow.api.ApiIntegrationTest;
import com.phananh.arenaflow.domain.entity.user.User;
import com.phananh.arenaflow.domain.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Chạy ở giai đoạn tích hợp (DB có V6–V11). */
class NotificationControllerTest extends ApiIntegrationTest {

    @Autowired private NotificationRepository notificationRepository;
    @Autowired private Clock clock;

    private User caller;
    private UUID notificationId;

    @BeforeEach
    void setUp() {
        caller = saveUser("Notified Caller");
        flush();
        notificationId = notificationRepository.insertAllReturning(new UUID[]{caller.getId()}, new String[]{"WELCOME"},
                new String[]{"Chào mừng bạn đến với ArenaFlow"}, new String[]{"Hoàn thiện hồ sơ."},
                new String[]{"USER"}, new UUID[]{caller.getId()}, LocalDateTime.now(clock)).getFirst().getId();
    }

    @Test
    void listCountReadAndReadAll() throws Exception {
        mockMvc.perform(as(caller, get("/api/v1/profile/notifications")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].notificationId").value(notificationId.toString()))
                .andExpect(jsonPath("$.data.items[0].type").value("WELCOME"))
                .andExpect(jsonPath("$.data.items[0].isRead").value(false))
                .andExpect(jsonPath("$.data.hasMore").value(false));
        mockMvc.perform(as(caller, get("/api/v1/profile/notifications/unread-count")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(1));
        mockMvc.perform(as(caller, post("/api/v1/profile/notifications/{id}/read", notificationId)))
                .andExpect(status().isNoContent());
        mockMvc.perform(as(caller, post("/api/v1/profile/notifications/read")))
                .andExpect(status().isNoContent());
        mockMvc.perform(as(caller, get("/api/v1/profile/notifications/unread-count")))
                .andExpect(jsonPath("$.data.unreadCount").value(0));
    }

    @Test
    void validationAndErrorCodes() throws Exception {
        mockMvc.perform(as(caller, get("/api/v1/profile/notifications")
                        .param("before", UUID.randomUUID().toString()).param("after", UUID.randomUUID().toString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mockMvc.perform(as(caller, get("/api/v1/profile/notifications").param("limit", "101")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(caller, get("/api/v1/profile/notifications").param("before", UUID.randomUUID().toString())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("NOTIFICATION_002"));

        User other = saveUser("Other");
        flush();
        mockMvc.perform(as(other, post("/api/v1/profile/notifications/{id}/read", notificationId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOTIFICATION_001"));
        mockMvc.perform(get("/api/v1/profile/notifications/unread-count"))
                .andExpect(status().isUnauthorized());
    }
}
