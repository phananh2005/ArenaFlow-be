package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.entity.chat.ConversationMessage;
import com.phananh.arenaflow.domain.projection.ChatCursorView;
import com.phananh.arenaflow.domain.projection.ConversationMessageView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tin nhắn. Phân trang theo con trỏ {@code (created_at, id)} (so sánh bộ hai — index theo cuộc trò chuyện + thời điểm +
 * id): id UUID v7 chỉ tăng chặt trong một JVM nên không dùng riêng id để sắp thứ tự. Mỗi câu JOIN người gửi.
 */
public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, UUID> {

    String MESSAGE_COLUMNS = """
            SELECT cm.id AS id, cm.conversation_id AS conversationId, cm.sender_party AS senderParty,
                   cm.sender_user_id AS senderUserId, su.display_name AS senderName, su.avatar_url AS senderAvatarUrl,
                   (su.deleted_at IS NOT NULL) AS senderDeleted, cm.message AS message, cm.is_read AS isRead,
                   cm.created_at AS createdAt
            FROM conversation_message cm
            JOIN "user" su ON su.id = cm.sender_user_id
            """;

    /** Con trỏ chỉ hợp lệ khi tin thuộc đúng cuộc trò chuyện; empty → CHAT_001. */
    @Query(value = """
            SELECT cm.created_at AS createdAt, cm.id AS id FROM conversation_message cm
            WHERE cm.conversation_id = :conversationId AND cm.id = :messageId
            """, nativeQuery = true)
    Optional<ChatCursorView> findCursor(@Param("conversationId") UUID conversationId,
                                        @Param("messageId") UUID messageId);

    /** Mới nhất trước. */
    @Query(value = MESSAGE_COLUMNS + """
            WHERE cm.conversation_id = :conversationId
            ORDER BY cm.created_at DESC, cm.id DESC LIMIT :limit
            """, nativeQuery = true)
    List<ConversationMessageView> findLatest(@Param("conversationId") UUID conversationId, @Param("limit") int limit);

    /** Cũ hơn con trỏ, mới nhất trước. */
    @Query(value = MESSAGE_COLUMNS + """
            WHERE cm.conversation_id = :conversationId AND (cm.created_at, cm.id) < (:createdAt, :id)
            ORDER BY cm.created_at DESC, cm.id DESC LIMIT :limit
            """, nativeQuery = true)
    List<ConversationMessageView> findBefore(@Param("conversationId") UUID conversationId,
                                             @Param("createdAt") LocalDateTime createdAt, @Param("id") UUID id,
                                             @Param("limit") int limit);

    /** Mới hơn con trỏ, cũ nhất trước (bù tin sau khi kết nối lại). */
    @Query(value = MESSAGE_COLUMNS + """
            WHERE cm.conversation_id = :conversationId AND (cm.created_at, cm.id) > (:createdAt, :id)
            ORDER BY cm.created_at ASC, cm.id ASC LIMIT :limit
            """, nativeQuery = true)
    List<ConversationMessageView> findAfter(@Param("conversationId") UUID conversationId,
                                            @Param("createdAt") LocalDateTime createdAt, @Param("id") UUID id,
                                            @Param("limit") int limit);

    /** Đánh dấu đã đọc mọi tin của bên kia; gọi lặp không đổi gì. */
    @Modifying
    @Query(value = """
            UPDATE conversation_message SET is_read = TRUE
            WHERE conversation_id = :conversationId AND sender_party <> :readerParty AND NOT is_read
            """, nativeQuery = true)
    int markOtherPartyRead(@Param("conversationId") UUID conversationId, @Param("readerParty") String readerParty);
}
