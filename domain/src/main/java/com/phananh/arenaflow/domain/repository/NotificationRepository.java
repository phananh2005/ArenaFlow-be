package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.entity.chat.Notification;
import com.phananh.arenaflow.domain.projection.InsertedNotificationView;
import com.phananh.arenaflow.domain.projection.NotificationCallerView;
import com.phananh.arenaflow.domain.projection.NotificationReadContextView;
import com.phananh.arenaflow.domain.projection.NotificationView;
import com.phananh.arenaflow.domain.projection.UnreadCountView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Thông báo của một người nhận. Mọi câu đọc lọc {@code created_at >= :visibleFrom} (thông báo quá hạn lưu không hiện
 * dù job xoá chưa chạy) và {@code recipient_user_id = :callerId}. Trang theo con trỏ {@code (created_at, id)}; mỗi
 * biến thể "chưa đọc" là một câu cố định riêng để planner dùng đúng index partial.
 */
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    String COLUMNS = "n.id AS id, n.type AS type, n.title AS title, n.body AS body, n.reference_type AS referenceType, "
            + "n.reference_id AS referenceId, n.is_read AS isRead, n.created_at AS createdAt ";

    /**
     * Cờ người gọi đã xoá + thời điểm của mốc con trỏ trong một câu. Rỗng khi người gọi không tồn tại;
     * {@code anchorCreatedAt} null khi không có mốc hoặc mốc không phải thông báo của người gọi trong hạn hiển thị.
     */
    @Query(value = """
            SELECT u.deleted_at IS NOT NULL AS callerDeleted, a.created_at AS anchorCreatedAt, a.id AS anchorId
            FROM "user" u
            LEFT JOIN notification a ON a.id = CAST(:anchorId AS uuid) AND a.recipient_user_id = u.id
                                     AND a.created_at >= :visibleFrom
            WHERE u.id = :callerId
            """, nativeQuery = true)
    Optional<NotificationCallerView> findCaller(@Param("callerId") UUID callerId, @Param("anchorId") UUID anchorId,
                                                @Param("visibleFrom") LocalDateTime visibleFrom);

    @Query(value = "SELECT " + COLUMNS + "FROM notification n WHERE n.recipient_user_id = :callerId "
            + "AND n.created_at >= :visibleFrom ORDER BY n.created_at DESC, n.id DESC LIMIT :pageLimit", nativeQuery = true)
    List<NotificationView> findLatest(@Param("callerId") UUID callerId, @Param("visibleFrom") LocalDateTime visibleFrom,
                                      @Param("pageLimit") int limit);

    @Query(value = "SELECT " + COLUMNS + "FROM notification n WHERE n.recipient_user_id = :callerId AND NOT n.is_read "
            + "AND n.created_at >= :visibleFrom ORDER BY n.created_at DESC, n.id DESC LIMIT :pageLimit", nativeQuery = true)
    List<NotificationView> findLatestUnread(@Param("callerId") UUID callerId,
                                            @Param("visibleFrom") LocalDateTime visibleFrom, @Param("pageLimit") int limit);

    /** Thông báo cũ hơn mốc, mới nhất trước. */
    @Query(value = "SELECT " + COLUMNS + "FROM notification n WHERE n.recipient_user_id = :callerId "
            + "AND n.created_at >= :visibleFrom AND (n.created_at, n.id) < (:anchorAt, :anchorId) "
            + "ORDER BY n.created_at DESC, n.id DESC LIMIT :pageLimit", nativeQuery = true)
    List<NotificationView> findBefore(@Param("callerId") UUID callerId, @Param("visibleFrom") LocalDateTime visibleFrom,
                                      @Param("anchorAt") LocalDateTime anchorAt, @Param("anchorId") UUID anchorId,
                                      @Param("pageLimit") int limit);

    @Query(value = "SELECT " + COLUMNS + "FROM notification n WHERE n.recipient_user_id = :callerId AND NOT n.is_read "
            + "AND n.created_at >= :visibleFrom AND (n.created_at, n.id) < (:anchorAt, :anchorId) "
            + "ORDER BY n.created_at DESC, n.id DESC LIMIT :pageLimit", nativeQuery = true)
    List<NotificationView> findBeforeUnread(@Param("callerId") UUID callerId,
                                            @Param("visibleFrom") LocalDateTime visibleFrom,
                                            @Param("anchorAt") LocalDateTime anchorAt, @Param("anchorId") UUID anchorId,
                                            @Param("pageLimit") int limit);

    /** Thông báo mới hơn mốc, cũ nhất trước. */
    @Query(value = "SELECT " + COLUMNS + "FROM notification n WHERE n.recipient_user_id = :callerId "
            + "AND n.created_at >= :visibleFrom AND (n.created_at, n.id) > (:anchorAt, :anchorId) "
            + "ORDER BY n.created_at ASC, n.id ASC LIMIT :pageLimit", nativeQuery = true)
    List<NotificationView> findAfter(@Param("callerId") UUID callerId, @Param("visibleFrom") LocalDateTime visibleFrom,
                                     @Param("anchorAt") LocalDateTime anchorAt, @Param("anchorId") UUID anchorId,
                                     @Param("pageLimit") int limit);

    @Query(value = "SELECT " + COLUMNS + "FROM notification n WHERE n.recipient_user_id = :callerId AND NOT n.is_read "
            + "AND n.created_at >= :visibleFrom AND (n.created_at, n.id) > (:anchorAt, :anchorId) "
            + "ORDER BY n.created_at ASC, n.id ASC LIMIT :pageLimit", nativeQuery = true)
    List<NotificationView> findAfterUnread(@Param("callerId") UUID callerId,
                                           @Param("visibleFrom") LocalDateTime visibleFrom,
                                           @Param("anchorAt") LocalDateTime anchorAt, @Param("anchorId") UUID anchorId,
                                           @Param("pageLimit") int limit);

    /** Cờ người gọi đã xoá + số chưa đọc trong hạn hiển thị, một câu. Rỗng khi người gọi không tồn tại. */
    @Query(value = """
            SELECT u.deleted_at IS NOT NULL AS callerDeleted,
                   (SELECT count(*) FROM notification n WHERE n.recipient_user_id = u.id AND NOT n.is_read
                    AND n.created_at >= :visibleFrom) AS unreadCount
            FROM "user" u WHERE u.id = :callerId
            """, nativeQuery = true)
    Optional<UnreadCountView> countUnread(@Param("callerId") UUID callerId,
                                          @Param("visibleFrom") LocalDateTime visibleFrom);

    /** Cờ người gọi đã xoá + thông báo (của người gọi, trong hạn hiển thị) có tồn tại / đã đọc chưa, một câu. */
    @Query(value = """
            SELECT u.deleted_at IS NOT NULL AS callerDeleted, n.id IS NOT NULL AS found,
                   COALESCE(n.is_read, FALSE) AS isRead
            FROM "user" u
            LEFT JOIN notification n ON n.id = :notificationId AND n.recipient_user_id = u.id
                                     AND n.created_at >= :visibleFrom
            WHERE u.id = :callerId
            """, nativeQuery = true)
    Optional<NotificationReadContextView> findReadContext(@Param("callerId") UUID callerId,
                                                          @Param("notificationId") UUID notificationId,
                                                          @Param("visibleFrom") LocalDateTime visibleFrom);

    /** Lọc người nhận ngay trong câu ghi; 0 dòng khi request song song đã đánh dấu. */
    @Modifying
    @Query(value = "UPDATE notification SET is_read = TRUE "
            + "WHERE id = :notificationId AND recipient_user_id = :callerId AND NOT is_read", nativeQuery = true)
    int markRead(@Param("callerId") UUID callerId, @Param("notificationId") UUID notificationId);

    /** Thông báo commit sau câu này (listener đồng thời) giữ nguyên chưa đọc. */
    @Modifying
    @Query(value = "UPDATE notification SET is_read = TRUE "
            + "WHERE recipient_user_id = :callerId AND NOT is_read AND created_at >= :visibleFrom", nativeQuery = true)
    int markAllRead(@Param("callerId") UUID callerId, @Param("visibleFrom") LocalDateTime visibleFrom);

    /**
     * Ghi cả lô thông báo trong một câu; mảng cùng độ dài, phần tử thứ i là một thông báo. Người nhận đã xoá tài khoản
     * bị loại ngay trong câu (JOIN {@code "user"}). Id do {@code uuidv7()} sinh, {@code created_at = :now}. Khai báo
     * không {@code @Modifying} vì câu trả về dòng (RETURNING).
     */
    @Query(value = """
            INSERT INTO notification (id, recipient_user_id, type, title, body, reference_type, reference_id, is_read, created_at)
            SELECT uuidv7(), t.recipient, t.type, t.title, t.body, t.ref_type, t.ref_id, FALSE, :now
            FROM unnest(CAST(:recipients AS uuid[]), CAST(:types AS text[]), CAST(:titles AS text[]),
                        CAST(:bodies AS text[]), CAST(:referenceTypes AS text[]), CAST(:referenceIds AS uuid[]))
                 WITH ORDINALITY AS t(recipient, type, title, body, ref_type, ref_id, ord)
            JOIN "user" u ON u.id = t.recipient AND u.deleted_at IS NULL
            ORDER BY t.ord
            RETURNING id AS id, recipient_user_id AS recipientUserId, type AS type, title AS title, body AS body,
                      reference_type AS referenceType, reference_id AS referenceId, created_at AS createdAt
            """, nativeQuery = true)
    List<InsertedNotificationView> insertAllReturning(@Param("recipients") UUID[] recipients,
                                                      @Param("types") String[] types,
                                                      @Param("titles") String[] titles,
                                                      @Param("bodies") String[] bodies,
                                                      @Param("referenceTypes") String[] referenceTypes,
                                                      @Param("referenceIds") UUID[] referenceIds,
                                                      @Param("now") LocalDateTime now);
}
