package com.phananh.arenaflow.application.mapper;

import com.phananh.arenaflow.application.dto.response.NotificationResponse;
import com.phananh.arenaflow.domain.entity.chat.Notification;
import com.phananh.arenaflow.domain.projection.NotificationView;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationMapperTest {

    private final NotificationMapper mapper = Mappers.getMapper(NotificationMapper.class);
    private final SpelAwareProxyProjectionFactory projections = new SpelAwareProxyProjectionFactory();

    @Test
    void mapsListRowIncludingNullReference() {
        UUID id = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 10, 9, 0);
        Map<String, Object> values = new HashMap<>();
        values.put("id", id);
        values.put("type", "WELCOME");
        values.put("title", "Chào mừng");
        values.put("body", "Nội dung");
        values.put("referenceType", null);
        values.put("referenceId", null);
        values.put("isRead", true);
        values.put("createdAt", createdAt);

        NotificationResponse response = mapper.toResponse(projections.createProjection(NotificationView.class, values));

        assertThat(response).isEqualTo(new NotificationResponse(id, Notification.Type.WELCOME, "Chào mừng", "Nội dung",
                null, null, true, createdAt));
    }
}
