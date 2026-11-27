package com.phananh.arenaflow.application.mapper;

import com.phananh.arenaflow.application.dto.response.NotificationResponse;
import com.phananh.arenaflow.domain.projection.InsertedNotificationView;
import com.phananh.arenaflow.domain.projection.NotificationView;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface NotificationMapper {

    @Mapping(target = "notificationId", source = "id")
    @Mapping(target = "isRead", source = "isRead")
    NotificationResponse toResponse(NotificationView view);

    /** Dòng vừa ghi luôn chưa đọc. */
    @Mapping(target = "notificationId", source = "id")
    @Mapping(target = "isRead", constant = "false")
    NotificationResponse toResponse(InsertedNotificationView view);
}
