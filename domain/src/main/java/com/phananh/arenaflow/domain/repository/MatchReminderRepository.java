package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.entity.chat.Notification;
import com.phananh.arenaflow.domain.projection.ReminderGameView;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Claim trận cần nhắc bằng cách chèn dấu vào {@code match_reminder}: unique theo (trận, loại nhắc) bảo đảm mỗi trận
 * được nhắc đúng một lần kể cả khi nhiều instance chạy trùng, không khoá dòng {@code match}. Câu trả về dòng
 * (RETURNING) nên khai báo không {@code @Modifying}.
 */
public interface MatchReminderRepository extends Repository<Notification, UUID> {

    /**
     * Kèo CONFIRMED / trận ngoài SCHEDULED (có ít nhất một mercenary đã duyệt) bắt đầu trong {@code (now, leadEnd]},
     * đã ghép / khai báo từ trước giờ bắt đầu ít nhất {@code leadSeconds} giây, chưa có dấu BEFORE_START.
     */
    @Query(value = """
            INSERT INTO match_reminder (id, kind, match_id, external_match_id, sent_at)
            SELECT uuidv7(), 'BEFORE_START', d.match_id, d.external_match_id, :now FROM (
                SELECT m.id AS match_id, CAST(NULL AS uuid) AS external_match_id, m.match_start_time AS start_time
                FROM match m
                LEFT JOIN match_request mr ON mr.match_id = m.id AND mr.status = 'ACCEPTED'
                LEFT JOIN pairing p ON p.match_id = m.id AND p.status = 'CONFIRMED'
                WHERE m.status = 'CONFIRMED' AND m.match_start_time > :now AND m.match_start_time <= :leadEnd
                  AND COALESCE(mr.responded_at, p.final_confirmed_at) <= m.match_start_time - make_interval(secs => :leadSeconds)
                  AND NOT EXISTS (SELECT 1 FROM match_reminder x WHERE x.match_id = m.id AND x.kind = 'BEFORE_START')
                UNION ALL
                SELECT NULL, e.id, e.match_start_time
                FROM external_match e
                WHERE e.status = 'SCHEDULED' AND e.match_start_time > :now AND e.match_start_time <= :leadEnd
                  AND e.created_at <= e.match_start_time - make_interval(secs => :leadSeconds)
                  AND NOT EXISTS (SELECT 1 FROM match_reminder x WHERE x.external_match_id = e.id AND x.kind = 'BEFORE_START')
                  AND EXISTS (SELECT 1 FROM mercenary_recruitment rc JOIN mercenary_application a ON a.recruitment_id = rc.id
                              WHERE rc.external_match_id = e.id AND a.status = 'APPROVED')
                ORDER BY start_time LIMIT :batchLimit) d
            ON CONFLICT DO NOTHING
            RETURNING match_id AS matchId, external_match_id AS externalMatchId
            """, nativeQuery = true)
    List<ReminderGameView> claimDueStart(@Param("now") LocalDateTime now, @Param("leadEnd") LocalDateTime leadEnd,
                                         @Param("leadSeconds") long leadSeconds, @Param("batchLimit") int limit);

    /**
     * Trận ngoài SCHEDULED kết thúc trong {@code (lookbackStart, now]}, có mercenary đã duyệt, chưa có dấu AFTER_END
     * → chèn dấu, trả id trận ngoài.
     */
    @Query(value = """
            INSERT INTO match_reminder (id, kind, external_match_id, sent_at)
            SELECT uuidv7(), 'AFTER_END', e.id, :now FROM external_match e
            WHERE e.status = 'SCHEDULED' AND e.match_end_time <= :now AND e.match_end_time > :lookbackStart
              AND NOT EXISTS (SELECT 1 FROM match_reminder x WHERE x.external_match_id = e.id AND x.kind = 'AFTER_END')
              AND EXISTS (SELECT 1 FROM mercenary_recruitment rc JOIN mercenary_application a ON a.recruitment_id = rc.id
                          WHERE rc.external_match_id = e.id AND a.status = 'APPROVED')
            ORDER BY e.match_end_time LIMIT :batchLimit
            ON CONFLICT DO NOTHING
            RETURNING external_match_id
            """, nativeQuery = true)
    List<UUID> claimDueRating(@Param("now") LocalDateTime now, @Param("lookbackStart") LocalDateTime lookbackStart,
                              @Param("batchLimit") int limit);
}
