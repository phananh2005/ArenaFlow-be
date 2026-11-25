package com.phananh.arenaflow.domain.projection;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Một dòng "Tin nhắn của tôi": cùng cột ngữ cảnh với {@link ConversationContextView} (để áp đúng chính sách quyền trong
 * bộ nhớ) + khoá sắp xếp, tin cuối (đã cắt 100 ký tự), cờ chưa đọc của từng bên, giờ bắt đầu trận.
 */
public interface ConversationListRowView extends ConversationContextView {
    LocalDateTime getLastActivityAt();
    UUID getLastMessageId();
    String getLastContentPreview();
    String getLastSenderParty();
    LocalDateTime getLastMessageCreatedAt();
    String getLastSenderName();
    Boolean getLastSenderDeleted();
    Boolean getUnreadFromA();
    Boolean getUnreadFromB();
    LocalDateTime getStartsAt();
}
