package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.entity.chat.Conversation;
import com.phananh.arenaflow.domain.projection.ConversationContextView;
import com.phananh.arenaflow.domain.projection.ConversationListRowView;
import com.phananh.arenaflow.domain.projection.MineCountView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    /** Ngữ cảnh một cuộc trò chuyện cho mọi loại ngữ cảnh, kèm cờ người gọi — một câu. */
    @Query(value = "SELECT " + ConversationSql.CONTEXT_COLUMNS + " FROM conversation c " + ConversationSql.CONTEXT_JOINS
            + " WHERE c.id = :id", nativeQuery = true)
    Optional<ConversationContextView> findContext(@Param("id") UUID id, @Param("callerId") UUID callerId,
                                                  @Param("now") LocalDateTime now);

    /**
     * Một trang "Tin nhắn của tôi", một câu: sắp theo hoạt động gần nhất (tin cuối; chưa có tin → lần gửi yêu cầu gần
     * nhất của luồng A, còn lại lúc mở) rồi id giảm dần; ngữ cảnh / tin cuối / cờ chưa đọc chỉ join cho các dòng của
     * trang. {@code UNION} bỏ trùng khi người gọi làm đội trưởng cả hai bên. Tin cuối cắt 100 ký tự ở SQL.
     */
    @Query(value = ConversationSql.MINE_CTE + """
            , ranked AS (
                SELECT c.id, COALESCE(lm.created_at, lrq.requested_at, c.created_at) AS last_activity_at,
                       lm.id AS last_message_id
                FROM mine x JOIN conversation c ON c.id = x.id
                LEFT JOIN LATERAL (SELECT cm.id, cm.created_at FROM conversation_message cm
                                   WHERE cm.conversation_id = c.id
                                   ORDER BY cm.created_at DESC, cm.id DESC LIMIT 1) lm ON true
                LEFT JOIN LATERAL (SELECT max(r.requested_at) AS requested_at FROM match_request r
                                   WHERE lm.id IS NULL AND c.context_type = 'TEAM_MATCH'
                                     AND r.match_id = c.match_id AND r.requester_team_id = c.party_b_id) lrq ON true
                ORDER BY last_activity_at DESC, c.id DESC
                LIMIT :limit OFFSET :offset)
            SELECT rk.last_activity_at AS lastActivityAt,
            """ + ConversationSql.CONTEXT_COLUMNS + """
            , lmsg.id AS lastMessageId, left(lmsg.message, 100) AS lastContentPreview,
            lmsg.sender_party AS lastSenderParty, lmsg.created_at AS lastMessageCreatedAt,
            su.display_name AS lastSenderName, (su.deleted_at IS NOT NULL) AS lastSenderDeleted,
            EXISTS (SELECT 1 FROM conversation_message u WHERE u.conversation_id = rk.id AND u.sender_party = 'A'
                    AND NOT u.is_read) AS unreadFromA,
            EXISTS (SELECT 1 FROM conversation_message u WHERE u.conversation_id = rk.id AND u.sender_party = 'B'
                    AND NOT u.is_read) AS unreadFromB,
            m.match_start_time AS startsAt
            FROM ranked rk JOIN conversation c ON c.id = rk.id
            LEFT JOIN conversation_message lmsg ON lmsg.id = rk.last_message_id
            LEFT JOIN "user" su ON su.id = lmsg.sender_user_id
            """ + ConversationSql.CONTEXT_JOINS + " ORDER BY rk.last_activity_at DESC, rk.id DESC",
            nativeQuery = true)
    List<ConversationListRowView> findMine(@Param("callerId") UUID callerId, @Param("now") LocalDateTime now,
                                           @Param("limit") int limit, @Param("offset") long offset);

    /** Tổng số cuộc trò chuyện của người gọi + cờ người gọi đã xoá (không có user coi như đã xoá) — một câu. */
    @Query(value = ConversationSql.MINE_CTE + """
            SELECT (SELECT count(*) FROM mine) AS total,
                   COALESCE((SELECT u.deleted_at IS NOT NULL FROM "user" u WHERE u.id = :callerId), true) AS callerDeleted
            """, nativeQuery = true)
    MineCountView countMine(@Param("callerId") UUID callerId);
}
