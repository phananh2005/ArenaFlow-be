package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.entity.team.Team;
import com.phananh.arenaflow.domain.projection.ScoreChangeView;
import com.phananh.arenaflow.domain.projection.TeamCaptainContextView;
import com.phananh.arenaflow.domain.projection.TeamDetailView;
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

    /** Trạng thái đội + cờ "userId là đội trưởng ACTIVE" + tên/ảnh/điểm đội + tên/cờ đã xoá của userId (một câu). */
    @Query(value = """
            SELECT t.status AS status, t.name AS name, t.avatar_url AS avatarUrl,
                   t.reliability_score AS reliabilityScore,
                   EXISTS (SELECT 1 FROM team_member m WHERE m.team_id = t.id AND m.user_id = :userId
                           AND m.role = 'CAPTAIN' AND m.status = 'ACTIVE') AS captain,
                   u.display_name AS userDisplayName, (u.deleted_at IS NOT NULL) AS userDeleted
            FROM team t
            LEFT JOIN "user" u ON u.id = :userId
            WHERE t.id = :teamId
            """, nativeQuery = true)
    Optional<TeamCaptainContextView> findCaptainContext(@Param("teamId") UUID teamId, @Param("userId") UUID userId);

    /**
     * Tăng bộ đếm report loại {@code type}, chuỗi trận sạch về 0, trừ điểm theo bậc (bộ đếm sau khi tăng, chặn ở bậc
     * 4), chặn dưới 0. Khoá dòng đội trước khi đọc điểm cũ. Trả điểm trước / sau.
     */
    @Query(value = """
            WITH locked AS (SELECT id, reliability_score AS old_score FROM team WHERE id = :id FOR NO KEY UPDATE)
            UPDATE team t SET
              report_count_noshow = CASE WHEN :type = 'NOSHOW' THEN t.report_count_noshow + 1 ELSE t.report_count_noshow END,
              report_count_bad_play = CASE WHEN :type = 'BAD_PLAY' THEN t.report_count_bad_play + 1
                                           ELSE t.report_count_bad_play END,
              report_count_bad_communication = CASE WHEN :type = 'BAD_COMMUNICATION'
                                           THEN t.report_count_bad_communication + 1
                                           ELSE t.report_count_bad_communication END,
              report_count_skill_mismatch = CASE WHEN :type = 'SKILL_MISMATCH' THEN t.report_count_skill_mismatch + 1
                                           ELSE t.report_count_skill_mismatch END,
              clean_streak = 0,
              reliability_score = GREATEST(0, t.reliability_score - (CAST(:tiers AS int[]))[LEAST(
                  CASE :type WHEN 'NOSHOW' THEN t.report_count_noshow
                             WHEN 'BAD_PLAY' THEN t.report_count_bad_play
                             WHEN 'BAD_COMMUNICATION' THEN t.report_count_bad_communication
                             ELSE t.report_count_skill_mismatch END + 1, 4)])
            FROM locked l WHERE t.id = l.id
            RETURNING 'TEAM' AS subjectType, t.id AS subjectId, l.old_score AS oldScore,
                      t.reliability_score AS newScore
            """, nativeQuery = true)
    Optional<ScoreChangeView> applyReportPenalty(@Param("id") UUID id, @Param("type") String type,
                                                 @Param("tiers") int[] tiers);

    /** Hoàn điểm khi report bị bác: cộng {@code points} (chặn 100), giảm bộ đếm loại đó (chặn 0); không khôi phục chuỗi. */
    @Modifying
    @Query(value = """
            UPDATE team SET
              reliability_score = LEAST(100, reliability_score + :points),
              report_count_noshow = CASE WHEN :type = 'NOSHOW' THEN GREATEST(0, report_count_noshow - 1)
                                         ELSE report_count_noshow END,
              report_count_bad_play = CASE WHEN :type = 'BAD_PLAY' THEN GREATEST(0, report_count_bad_play - 1)
                                           ELSE report_count_bad_play END,
              report_count_bad_communication = CASE WHEN :type = 'BAD_COMMUNICATION'
                                           THEN GREATEST(0, report_count_bad_communication - 1)
                                           ELSE report_count_bad_communication END,
              report_count_skill_mismatch = CASE WHEN :type = 'SKILL_MISMATCH'
                                           THEN GREATEST(0, report_count_skill_mismatch - 1)
                                           ELSE report_count_skill_mismatch END
            WHERE id = :id
            """, nativeQuery = true)
    int refundReportPenalty(@Param("id") UUID id, @Param("type") String type, @Param("points") int points);

    /**
     * Cấp / thu hồi huy hiệu đội theo điều kiện hiện tại; chỉ ghi dòng đổi trạng thái → trả các đội vừa đổi.
     */
    @Query(value = """
            UPDATE team t SET verified_at = CASE WHEN e.eligible THEN :now ELSE NULL END
            FROM (SELECT t2.id,
                         COALESCE(EXISTS (SELECT 1 FROM team_member cm JOIN "user" u ON u.id = cm.user_id
                                          WHERE cm.team_id = t2.id AND cm.role = 'CAPTAIN' AND cm.status = 'ACTIVE'
                                            AND u.phone_number IS NOT NULL AND u.deleted_at IS NULL)
                                  AND s.completed_matches > :minCompleted AND s.avg_rating > :minAvg
                                  AND t2.reliability_score >= :minScore, false) AS eligible
                  FROM team t2 CROSS JOIN LATERAL (""" + TeamReputationSql.STATS_LATERAL + """
                  ) s WHERE t2.id = ANY(CAST(:ids AS uuid[]))) e
            WHERE t.id = e.id AND (t.verified_at IS NOT NULL) IS DISTINCT FROM e.eligible
            RETURNING t.id AS subjectId, (t.verified_at IS NOT NULL) AS verified
            """, nativeQuery = true)
    List<VerificationChangeView> refreshVerification(@Param("ids") UUID[] ids, @Param("now") LocalDateTime now,
                                                     @Param("minCompleted") int minCompleted,
                                                     @Param("minAvg") double minAvg, @Param("minScore") int minScore);
}
