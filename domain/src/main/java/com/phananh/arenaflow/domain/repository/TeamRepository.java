package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.entity.team.Team;
import com.phananh.arenaflow.domain.projection.ScoreChangeView;
import com.phananh.arenaflow.domain.projection.TeamCaptainContextView;
import com.phananh.arenaflow.domain.projection.TeamDetailView;
import com.phananh.arenaflow.domain.projection.TeamReputationView;
import com.phananh.arenaflow.domain.projection.TeamSummaryView;
import com.phananh.arenaflow.domain.projection.VerificationChangeView;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeamRepository extends JpaRepository<Team, UUID> {

    /** Đọc đội để mời thành viên; {@code version} tăng lúc commit để tuần tự hoá với disband/invite. */
    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("SELECT t FROM Team t WHERE t.id = :id")
    Optional<Team> findForInviteById(@Param("id") UUID id);

    /**
     * Trạng thái đã commit của đội (native → không lấy từ persistence context). Dùng ở nhánh lỗi của guard để phân biệt
     * "đội vừa bị giải tán" (TEAM_016) với lỗi quyền/tư cách khi disband commit chen giữa các câu đọc (READ COMMITTED).
     */
    @Query(value = "SELECT status FROM team WHERE id = :id", nativeQuery = true)
    Optional<String> findCurrentStatusById(@Param("id") UUID id);

    /**
     * Chi tiết đội trong một câu: captain ACTIVE (LEFT JOIN — null khi đội không còn captain, vd đã giải tán), người
     * giải tán, cờ user đã xoá của cả hai (để che tên) và memberCount đếm thành viên ACTIVE. Không lọc theo trạng thái
     * đội.
     */
    @Query(value = """
            SELECT t.id AS teamId, t.name AS teamName, t.avatar_url AS avatarUrl, t.description AS description,
                   t.skill_levels AS skillLevels, t.status AS status, t.inactive_reason AS inactiveReason,
                   cu.id AS captainId, cu.display_name AS captainName,
                   (cu.deleted_at IS NOT NULL) AS captainDeleted,
                   (SELECT COUNT(*) FROM team_member am WHERE am.team_id = t.id AND am.status = 'ACTIVE') AS memberCount,
                   t.created_by AS createdBy, t.created_at AS createdAt, t.updated_at AS updatedAt,
                   t.disband_reason AS disbandReason, t.disbanded_at AS disbandedAt, t.disbanded_by AS disbandedById,
                   du.display_name AS disbandedByName, (du.deleted_at IS NOT NULL) AS disbandedByDeleted
            FROM team t
            LEFT JOIN team_member cm ON cm.team_id = t.id AND cm.role = 'CAPTAIN' AND cm.status = 'ACTIVE'
            LEFT JOIN "user" cu ON cu.id = cm.user_id
            LEFT JOIN "user" du ON du.id = t.disbanded_by
            WHERE t.id = :teamId
            """, nativeQuery = true)
    Optional<TeamDetailView> findDetailById(@Param("teamId") UUID teamId);

    /** {@code status} null = tất cả. Sort cố định: created_at DESC, id DESC (index idx_team_status_created). */
    @Query(value = """
            SELECT t.id AS teamId, t.name AS teamName, t.avatar_url AS avatarUrl,
                   t.skill_levels AS skillLevels, t.status AS status, t.inactive_reason AS inactiveReason,
                   cu.display_name AS captainName, (cu.deleted_at IS NOT NULL) AS captainDeleted,
                   (SELECT COUNT(*) FROM team_member am WHERE am.team_id = t.id AND am.status = 'ACTIVE') AS memberCount,
                   t.created_at AS createdAt,
                   du.display_name AS disbandedByName, (du.deleted_at IS NOT NULL) AS disbandedByDeleted
            FROM team t
            LEFT JOIN team_member cm ON cm.team_id = t.id AND cm.role = 'CAPTAIN' AND cm.status = 'ACTIVE'
            LEFT JOIN "user" cu ON cu.id = cm.user_id
            LEFT JOIN "user" du ON du.id = t.disbanded_by
            WHERE (CAST(:status AS VARCHAR) IS NULL OR t.status = CAST(:status AS VARCHAR))
            ORDER BY t.created_at DESC, t.id DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM team t
            WHERE (CAST(:status AS VARCHAR) IS NULL OR t.status = CAST(:status AS VARCHAR))
            """,
            nativeQuery = true)
    Page<TeamSummaryView> findSummaries(@Param("status") String status, Pageable pageable);

    /**
     * Trạng thái đội + cờ {@code userId} là đội trưởng ACTIVE + tên/ảnh/điểm đội + tên/cờ đã xoá của {@code userId},
     * trong một câu. Đội không tồn tại → empty. Đội đã giải tán không còn đội trưởng ACTIVE nên {@code captain} false.
     */
    @Query(value = """
            SELECT t.status AS status,
                   EXISTS (SELECT 1 FROM team_member m WHERE m.team_id = t.id AND m.user_id = :userId
                           AND m.role = 'CAPTAIN' AND m.status = 'ACTIVE') AS captain,
                   t.name AS name, t.avatar_url AS avatarUrl, t.reliability_score AS reliabilityScore,
                   u.display_name AS userDisplayName, (u.deleted_at IS NOT NULL) AS userDeleted
            FROM team t
            LEFT JOIN "user" u ON u.id = :userId
            WHERE t.id = :teamId
            """, nativeQuery = true)
    Optional<TeamCaptainContextView> findCaptainContext(@Param("teamId") UUID teamId, @Param("userId") UUID userId);

    /**
     * Cộng/trừ điểm uy tín nguyên tử, chặn trong [0, 100]; {@code breaksCleanStreak} đặt chuỗi trận sạch về 0 trong
     * cùng câu. Không tăng version: cột điểm và chuỗi trận sạch đều khoá {@code updatable = false} trên entity nên
     * không bị dirty checking ghi đè khi sửa hoặc giải tán đội.
     */
    @Modifying
    @Query(value = """
            UPDATE team SET reliability_score = LEAST(100, GREATEST(0, reliability_score + :delta)),
                   clean_streak = CASE WHEN :breaksCleanStreak THEN 0 ELSE clean_streak END
            WHERE id = :id
            """, nativeQuery = true)
    int adjustReliability(@Param("id") UUID id, @Param("delta") int delta,
                          @Param("breaksCleanStreak") boolean breaksCleanStreak);

    /**
     * Khoá dòng các đội theo thứ tự id (chống deadlock giữa hai trận cùng cặp đội) trước câu cập nhật uy tín đọc bảng
     * khác: câu sau lấy ảnh chụp khi đã giữ khoá nên thấy mọi report đã commit trong lúc chờ. Trả id đã khoá.
     */
    @Query(value = """
            SELECT id FROM team WHERE id = ANY(CAST(:ids AS uuid[])) ORDER BY id FOR NO KEY UPDATE
            """, nativeQuery = true)
    List<UUID> lockForReputation(@Param("ids") UUID[] ids);

    /**
     * Một câu cho mọi đội khi trận chuyển COMPLETED, chạy sau {@link #lockForReputation} trong cùng transaction (xét
     * report bằng ảnh chụp đầu câu): khoá các đội theo thứ tự id, cộng điểm hoàn thành + thưởng 5 sao, xét chuỗi trận
     * sạch (trận "sạch" = đội không bị đối thủ thật của trận report, report chưa bị bác), thưởng mốc chuỗi (bội số của 2 × mốc → thưởng lớn, bội số của mốc → thưởng nhỏ) và
     * đưa mọi bộ đếm report về 0 khi chạm mốc; chặn điểm [0, 100]. Entry {@code completes = false} chỉ cộng
     * {@code bonus}, giữ chuỗi. Trả điểm trước (đọc sau khi khoá) và sau của từng đội. Khai báo không {@code @Modifying}:
     * câu trả về dòng nên Spring Data chạy như query.
     */
    @Query(value = """
            WITH input AS (
                SELECT * FROM unnest(CAST(:ids AS uuid[]), CAST(:completes AS boolean[]), CAST(:bonuses AS int[]))
                    AS i(team_id, completes, bonus)),
            locked AS (
                SELECT t.id, t.reliability_score AS old_score, t.clean_streak AS old_streak
                FROM team t JOIN input i ON i.team_id = t.id
                ORDER BY t.id
                FOR NO KEY UPDATE OF t),
            calc AS (
                SELECT l.id, l.old_score, i.completes, i.bonus, l.old_streak + 1 AS next_streak,
                       i.completes AND NOT EXISTS (
                           SELECT 1 FROM report r JOIN match m ON m.id = r.match_id
                           WHERE r.match_id = :matchId AND r.reported_type = 'TEAM' AND r.reported_id = l.id
                             AND r.status <> 'REJECTED' AND r.reporter_type = 'TEAM'
                             AND r.reporter_id IN (m.author_team_id, m.confirmed_opponent_team_id)) AS clean
                FROM locked l JOIN input i ON i.team_id = l.id),
            x AS (
                SELECT c.*, (c.clean AND c.next_streak % :milestone = 0) AS hit FROM calc c)
            UPDATE team t SET
                clean_streak = CASE WHEN NOT x.completes THEN t.clean_streak WHEN x.clean THEN x.next_streak ELSE 0 END,
                reliability_score = LEAST(100, GREATEST(0, t.reliability_score + x.bonus
                    + CASE WHEN x.completes THEN :completionPoints ELSE 0 END
                    + CASE WHEN x.clean AND x.next_streak % (2 * :milestone) = 0 THEN :bonus10
                           WHEN x.hit THEN :bonus5 ELSE 0 END)),
                report_count_noshow = CASE WHEN x.hit THEN 0 ELSE t.report_count_noshow END,
                report_count_bad_play = CASE WHEN x.hit THEN 0 ELSE t.report_count_bad_play END,
                report_count_bad_communication = CASE WHEN x.hit THEN 0 ELSE t.report_count_bad_communication END,
                report_count_skill_mismatch = CASE WHEN x.hit THEN 0 ELSE t.report_count_skill_mismatch END
            FROM x
            WHERE t.id = x.id
            RETURNING t.id AS teamId, x.old_score AS oldScore, t.reliability_score AS newScore
            """, nativeQuery = true)
    List<ScoreChangeView> applyCompletion(@Param("matchId") UUID matchId, @Param("ids") UUID[] ids,
                                          @Param("completes") boolean[] completes, @Param("bonuses") int[] bonuses,
                                          @Param("completionPoints") int completionPoints,
                                          @Param("milestone") int milestone, @Param("bonus5") int bonus5,
                                          @Param("bonus10") int bonus10);

    /**
     * Khoá đội, tăng bộ đếm report của loại {@code type}, đưa chuỗi trận sạch về 0, trừ điểm theo bậc
     * {@code tiers[min(bộ đếm sau khi tăng, 4)]} (chặn 0). Cột bộ đếm chọn bằng CASE theo tham số, không nối chuỗi tên
     * cột. Điểm cũ và bộ đếm đọc ngay trên dòng đã khoá: khi phải chờ khoá, dòng khoá là bản mới nhất còn lần quét
     * bảng khác trong cùng câu vẫn dùng ảnh chụp đầu câu. Trả điểm trước / sau; không {@code @Modifying} vì trả về dòng.
     */
    @Query(value = """
            WITH locked AS (
                SELECT id, reliability_score AS old_score,
                       CASE CAST(:type AS varchar)
                           WHEN 'NOSHOW' THEN report_count_noshow
                           WHEN 'BAD_PLAY' THEN report_count_bad_play
                           WHEN 'BAD_COMMUNICATION' THEN report_count_bad_communication
                           ELSE report_count_skill_mismatch END + 1 AS next_count
                FROM team WHERE id = :id FOR NO KEY UPDATE)
            UPDATE team t SET
                report_count_noshow = t.report_count_noshow
                    + CASE WHEN CAST(:type AS varchar) = 'NOSHOW' THEN 1 ELSE 0 END,
                report_count_bad_play = t.report_count_bad_play
                    + CASE WHEN CAST(:type AS varchar) = 'BAD_PLAY' THEN 1 ELSE 0 END,
                report_count_bad_communication = t.report_count_bad_communication
                    + CASE WHEN CAST(:type AS varchar) = 'BAD_COMMUNICATION' THEN 1 ELSE 0 END,
                report_count_skill_mismatch = t.report_count_skill_mismatch
                    + CASE WHEN CAST(:type AS varchar) = 'SKILL_MISMATCH' THEN 1 ELSE 0 END,
                clean_streak = 0,
                reliability_score = GREATEST(0, t.reliability_score - (CAST(:tiers AS int[]))[LEAST(l.next_count, 4)])
            FROM locked l
            WHERE t.id = l.id
            RETURNING t.id AS teamId, l.old_score AS oldScore, t.reliability_score AS newScore
            """, nativeQuery = true)
    Optional<ScoreChangeView> applyReportPenalty(@Param("id") UUID id, @Param("type") String type,
                                                 @Param("tiers") int[] tiers);

    /**
     * Hoàn {@code points} điểm (chặn 100) và giảm bộ đếm loại {@code type} đi 1 (không âm — bộ đếm có thể đã về 0 do
     * chạm mốc chuỗi). Không khôi phục chuỗi trận sạch.
     */
    @Modifying
    @Query(value = """
            UPDATE team SET
                reliability_score = LEAST(100, reliability_score + :points),
                report_count_noshow = CASE WHEN CAST(:type AS varchar) = 'NOSHOW'
                    THEN GREATEST(0, report_count_noshow - 1) ELSE report_count_noshow END,
                report_count_bad_play = CASE WHEN CAST(:type AS varchar) = 'BAD_PLAY'
                    THEN GREATEST(0, report_count_bad_play - 1) ELSE report_count_bad_play END,
                report_count_bad_communication = CASE WHEN CAST(:type AS varchar) = 'BAD_COMMUNICATION'
                    THEN GREATEST(0, report_count_bad_communication - 1) ELSE report_count_bad_communication END,
                report_count_skill_mismatch = CASE WHEN CAST(:type AS varchar) = 'SKILL_MISMATCH'
                    THEN GREATEST(0, report_count_skill_mismatch - 1) ELSE report_count_skill_mismatch END
            WHERE id = :id
            """, nativeQuery = true)
    int refundReportPenalty(@Param("id") UUID id, @Param("type") String type, @Param("points") int points);

    /** Uy tín đội trong một câu: điểm, huy hiệu, thống kê tính khi đọc. Không lọc theo trạng thái đội. */
    @Query(value = """
            SELECT tx.id AS teamId, tx.reliability_score AS reliabilityScore, tx.verified_at AS verifiedAt,
                   s.completed_matches AS completedMatches, s.counted_matches AS countedMatches,
                   s.avg_rating AS averageRating, s.rating_count AS ratingCount
            FROM team tx
            CROSS JOIN LATERAL (""" + TeamReputationSql.STATS + """
            ) s
            WHERE tx.id = :teamId
            """, nativeQuery = true)
    Optional<TeamReputationView> findReputation(@Param("teamId") UUID teamId,
                                                @Param("cancelWindowHours") int cancelWindowHours);

    /**
     * Cấp / thu hồi huy hiệu cho các đội {@code ids} theo dữ liệu gốc (đội trưởng hiện tại có số điện thoại, số trận
     * hoàn thành &gt; ngưỡng, sao trung bình &gt; ngưỡng, điểm ≥ ngưỡng). Chỉ ghi đội có trạng thái huy hiệu đổi → hai
     * lần chạy đồng thời không trả trùng; trả các đội vừa đổi. Không {@code @Modifying} vì trả về dòng.
     */
    @Query(value = """
            UPDATE team t SET verified_at = CASE WHEN e.eligible THEN CAST(:now AS timestamp) ELSE NULL END
            FROM (
                SELECT tx.id,
                       COALESCE(EXISTS (SELECT 1 FROM team_member cm JOIN "user" u ON u.id = cm.user_id
                                        WHERE cm.team_id = tx.id AND cm.role = 'CAPTAIN' AND cm.status = 'ACTIVE'
                                          AND u.phone_number IS NOT NULL AND u.deleted_at IS NULL)
                                AND s.completed_matches > :minCompleted AND s.avg_rating > :minAvg
                                AND tx.reliability_score >= :minScore, false) AS eligible
                FROM team tx
                CROSS JOIN LATERAL (""" + TeamReputationSql.STATS + """
                ) s
                WHERE tx.id = ANY(CAST(:ids AS uuid[]))
            ) e
            WHERE t.id = e.id AND (t.verified_at IS NOT NULL) IS DISTINCT FROM e.eligible
            RETURNING t.id AS teamId, (t.verified_at IS NOT NULL) AS verified
            """, nativeQuery = true)
    List<VerificationChangeView> refreshVerification(@Param("ids") UUID[] ids, @Param("now") LocalDateTime now,
                                                     @Param("minCompleted") int minCompleted,
                                                     @Param("minAvg") double minAvg, @Param("minScore") int minScore,
                                                     @Param("cancelWindowHours") int cancelWindowHours);

    /** Lô id đội tăng dần sau {@code afterId} (null = từ đầu) cho job đối soát huy hiệu. */
    @Query(value = """
            SELECT id FROM team
            WHERE CAST(:afterId AS uuid) IS NULL OR id > CAST(:afterId AS uuid)
            ORDER BY id
            LIMIT :limit
            """, nativeQuery = true)
    List<UUID> findIdsForVerification(@Param("afterId") UUID afterId, @Param("limit") int limit);
}
