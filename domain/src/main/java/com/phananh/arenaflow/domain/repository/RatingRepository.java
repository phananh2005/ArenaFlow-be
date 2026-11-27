package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.entity.rating.Rating;
import com.phananh.arenaflow.domain.projection.MercenaryRatingContextView;
import com.phananh.arenaflow.domain.projection.TeamMercenaryRatingView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Đánh giá sau trận (đội ↔ đội, đội ↔ mercenary). */
public interface RatingRepository extends JpaRepository<Rating, UUID> {

    /** Đánh giá hai phía của một đơn, cũ nhất trước (0–2 dòng). */
    List<Rating> findByApplicationIdOrderByCreatedAtAsc(UUID applicationId);

    /**
     * Context đánh giá theo đơn, xuất phát từ người gọi (luôn một dòng; đơn không có → {@code applicationId} null): trạng
     * thái hiệu lực của đơn, trận đã kết thúc chưa, đội tuyển, cờ người gọi là đội trưởng ACTIVE hiện tại / ứng viên, và
     * đánh giá đã có của từng phía (mỗi phía tối đa một nhờ unique index).
     */
    @Query(value = "SELECT (me.deleted_at IS NOT NULL) AS callerDeleted, a.id AS applicationId,"
            + " CASE WHEN a.id IS NULL THEN NULL ELSE " + MercenarySql.APPLICATION_EFFECTIVE_STATUS
            + " END AS applicationStatus, a.applicant_id AS applicantId, r.match_id AS matchId,"
            + " r.external_match_id AS externalMatchId, " + MercenarySql.GAME_ENDED + " AS gameEnded,"
            + " t.id AS teamId, t.status AS teamStatus, COALESCE(cm.user_id = :callerId, false) AS callerCaptain,"
            + " COALESCE(a.applicant_id = :callerId, false) AS callerApplicant," + """

                   tr.id AS teamRatingId, tr.is_no_show AS teamRatingNoShow, tr.score AS teamRatingScore,
                   tr.comment AS teamRatingComment, tr.created_at AS teamRatingCreatedAt,
                   mr.id AS mercenaryRatingId, mr.score AS mercenaryRatingScore, mr.comment AS mercenaryRatingComment,
                   mr.created_at AS mercenaryRatingCreatedAt
            FROM "user" me
            LEFT JOIN mercenary_application a ON a.id = :applicationId
            LEFT JOIN mercenary_recruitment r ON r.id = a.recruitment_id
            LEFT JOIN match m ON m.id = r.match_id
            LEFT JOIN external_match x ON x.id = r.external_match_id
            LEFT JOIN team t ON t.id = r.team_id
            LEFT JOIN team_member cm ON cm.team_id = t.id AND cm.role = 'CAPTAIN' AND cm.status = 'ACTIVE'
            LEFT JOIN rating tr ON tr.application_id = a.id AND tr.rater_type = 'TEAM'
            LEFT JOIN rating mr ON mr.application_id = a.id AND mr.rater_type = 'INDIVIDUAL'
            WHERE me.id = :callerId
            """, nativeQuery = true)
    Optional<MercenaryRatingContextView> findMercenaryRatingContext(@Param("applicationId") UUID applicationId,
                                                                    @Param("callerId") UUID callerId,
                                                                    @Param("now") LocalDateTime now);

    /**
     * Đánh giá có sao của mercenary dành cho đội — chỉ đơn mà đội cũng đã đánh giá lại mercenary. Mới nhất trước.
     */
    @Query(value = """
            SELECT mr.id AS ratingId, mr.match_id AS matchId, mr.external_match_id AS externalMatchId,
                   COALESCE(m.match_start_time, x.match_start_time) AS gameStart,
                   u.id AS userId, u.display_name AS displayName, u.avatar_url AS avatarUrl,
                   (u.deleted_at IS NOT NULL) AS deleted, mr.score AS score, mr.comment AS comment,
                   mr.created_at AS createdAt
            FROM rating mr
            JOIN "user" u ON u.id = mr.rater_id
            LEFT JOIN match m ON m.id = mr.match_id
            LEFT JOIN external_match x ON x.id = mr.external_match_id
            WHERE mr.ratee_type = 'TEAM' AND mr.ratee_id = :teamId AND mr.rater_type = 'INDIVIDUAL'
              AND mr.application_id IS NOT NULL AND mr.score IS NOT NULL
              AND EXISTS (SELECT 1 FROM rating tr WHERE tr.application_id = mr.application_id AND tr.rater_type = 'TEAM')
            ORDER BY mr.created_at DESC, mr.id DESC
            """, countQuery = """
            SELECT COUNT(*) FROM rating mr
            WHERE mr.ratee_type = 'TEAM' AND mr.ratee_id = :teamId AND mr.rater_type = 'INDIVIDUAL'
              AND mr.application_id IS NOT NULL AND mr.score IS NOT NULL
              AND EXISTS (SELECT 1 FROM rating tr WHERE tr.application_id = mr.application_id AND tr.rater_type = 'TEAM')
            """, nativeQuery = true)
    Page<TeamMercenaryRatingView> findMercenaryRatingsOfTeam(@Param("teamId") UUID teamId, Pageable pageable);
}
