package com.phananh.arenaflow.application.dto.response;

import java.util.List;

public record ChatHistoryResponse(List<ConversationMessageResponse> items, boolean hasMore) {
}
