package com.phananh.arenaflow.application.dto.response;

import java.util.List;

public record NotificationHistoryResponse(List<NotificationResponse> items, boolean hasMore) {
}
