package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.entity.chat.Notification;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Xoá dữ liệu hết hạn lưu theo lô. Subquery khoá {@code FOR UPDATE … SKIP LOCKED}: hai instance chạy trùng không xoá
 * cùng dòng; chạy lại vô hại vì điều kiện theo thời gian.
 */
public interface RetentionRepository extends Repository<Notification, UUID> {

    /**
     * Cuộc trò chuyện có trận đã kết thúc trước {@code cutoff}: kèo COMPLETED/ARCHIVED theo giờ kết thúc, CANCELLED
     * theo lúc huỷ, EXPIRED theo hạn; trận ngoài SCHEDULED theo giờ kết thúc, CANCELLED theo lúc huỷ. Tin nhắn xoá
     * theo ON DELETE CASCADE.
     */
    @Modifying
    @Query(value = """
            DELETE FROM conversation WHERE id IN (
                SELECT c.id FROM conversation c
                LEFT JOIN match m ON m.id = c.match_id
                LEFT JOIN external_match e ON e.id = c.external_match_id
                WHERE (m.status IN ('COMPLETED', 'ARCHIVED') AND m.match_end_time < :cutoff)
                   OR (m.status = 'CANCELLED' AND m.cancelled_at < :cutoff)
                   OR (m.status = 'EXPIRED' AND m.expires_at < :cutoff)
                   OR (e.status = 'SCHEDULED' AND e.match_end_time < :cutoff)
                   OR (e.status = 'CANCELLED' AND e.cancelled_at < :cutoff)
                ORDER BY c.id LIMIT :batchLimit FOR UPDATE OF c SKIP LOCKED)
            """, nativeQuery = true)
    int deleteExpiredConversations(@Param("cutoff") LocalDateTime cutoff, @Param("batchLimit") int limit);

    @Modifying
    @Query(value = """
            DELETE FROM notification WHERE id IN (
                SELECT id FROM notification WHERE created_at < :cutoff ORDER BY created_at LIMIT :batchLimit
                FOR UPDATE SKIP LOCKED)
            """, nativeQuery = true)
    int deleteExpiredNotifications(@Param("cutoff") LocalDateTime cutoff, @Param("batchLimit") int limit);

    @Modifying
    @Query(value = "DELETE FROM match_reminder WHERE sent_at < :cutoff", nativeQuery = true)
    int deleteRemindersSentBefore(@Param("cutoff") LocalDateTime cutoff);
}
