package com.phananh.arenaflow.domain.projection;

import java.time.LocalDateTime;

/**
 * Ngữ cảnh report "giao tiếp tệ" qua chat: ngữ cảnh chung của cuộc trò chuyện + giờ trận + đã có tin nhắn + mỗi chiều
 * (bên A → bên B, bên B → bên A) đã có report giao tiếp tệ trong kèo chưa.
 */
public interface ChatReportContextView extends ConversationContextView {

    LocalDateTime getMatchStartTime();

    Boolean getHasMessages();

    Boolean getAReportedB();

    Boolean getBReportedA();
}
