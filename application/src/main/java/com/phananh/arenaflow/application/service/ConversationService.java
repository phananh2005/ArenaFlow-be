package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.ChatHistoryQuery;
import com.phananh.arenaflow.application.dto.request.SendConversationMessageRequest;
import com.phananh.arenaflow.application.dto.response.ChatHistoryResponse;
import com.phananh.arenaflow.application.dto.response.ConversationMessageResponse;
import com.phananh.arenaflow.application.dto.response.ConversationResponse;
import com.phananh.arenaflow.application.dto.response.ConversationSummaryResponse;
import com.phananh.arenaflow.application.dto.response.PageResponse;

import java.util.UUID;

/**
 * Lõi chat dùng chung cho mọi loại ngữ cảnh. Thứ tự kiểm chung: CHAT_002 (không có) → USER_001 (người gọi đã xoá) →
 * CHAT_003 (không tham gia). Quy tắc quyền theo loại ngữ cảnh nằm ở chính sách tương ứng.
 */
public interface ConversationService {

    /** Đầu phòng chat (mọi trạng thái). 1 truy vấn. */
    ConversationResponse getConversation(UUID conversationId, UUID callerId);

    /**
     * Gửi tin: … → CHAT_003 → yêu cầu còn chờ trên kèo đã tới hạn: commit chuyển EXPIRED rồi MATCH_004 → chỉ xem:
     * CHAT_004. Đẩy frame MESSAGE_CREATED sau commit. Thử lại khi deadlock, hết lượt → MATCH_014. 2 truy vấn.
     */
    ConversationMessageResponse send(UUID conversationId, UUID callerId, SendConversationMessageRequest request);

    /** Lịch sử theo con trỏ (mọi trạng thái); con trỏ thuộc cuộc trò chuyện khác → CHAT_001. 2–3 truy vấn. */
    ChatHistoryResponse history(UUID conversationId, UUID callerId, ChatHistoryQuery query);

    /** Đánh dấu đã đọc mọi tin của bên kia (mọi trạng thái). 2 truy vấn. */
    void markRead(UUID conversationId, UUID callerId);

    /** Kiểm quyền đăng ký topic realtime của cuộc trò chuyện; lỗi ném {@code BusinessException}. 1 truy vấn. */
    void requireSubscribable(UUID conversationId, UUID userId);

    /** "Tin nhắn của tôi": đếm (+ cờ đã xoá → USER_001), đếm 0 thì bỏ câu trang. ≤ 2 truy vấn. */
    PageResponse<ConversationSummaryResponse> listMyConversations(UUID callerId, int page, int pageSize);
}
