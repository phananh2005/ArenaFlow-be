package com.phananh.arenaflow.application.dto.request;

import com.phananh.arenaflow.application.support.chat.ConversationRules;
import com.phananh.arenaflow.application.validation.ValidChatCursor;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.UUID;

/** Con trỏ lịch sử chat: tối đa một trong {@code before} / {@code after}; {@code limit} mặc định 50. */
@ValidChatCursor
public record ChatHistoryQuery(UUID before, UUID after, @Min(1) @Max(ConversationRules.MAX_HISTORY_LIMIT) Integer limit) {

    public ChatHistoryQuery {
        limit = limit == null ? ConversationRules.DEFAULT_HISTORY_LIMIT : limit;
    }
}
