package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.entity.user.User;
import com.phananh.arenaflow.domain.projection.ProfileAreaView;
import com.phananh.arenaflow.domain.projection.ProfileView;
import com.phananh.arenaflow.domain.projection.ScoreChangeView;
import com.phananh.arenaflow.domain.projection.UserReputationView;
import com.phananh.arenaflow.domain.projection.VerificationChangeView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Truy cập bảng {@code "user"}: tìm user chưa xoá mềm, hồ sơ kèm địa giới, xoá mềm idempotent. */
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByIdAndDeletedAtIsNull(UUID id);

    /**
     * Hồ sơ + phường/xã + tỉnh trong một câu; user đã xoá mềm → empty. Không có ward → các field ward/province null.
     */
    @Query(value = """
            SELECT u.id AS userId, u.role AS role, u.phone_number AS phoneNumber,
                   u.display_name AS displayName, u.avatar_url AS avatarUrl, u.bio AS bio,
                   u.preferred_position AS preferredPosition, u.date_of_birth AS dateOfBirth,
                   u.gender AS gender,
                   w.id AS wardId, w.name AS wardName, p.id AS provinceId, p.name AS provinceName,
                   u.created_at AS createdAt, u.updated_at AS updatedAt
            FROM "user" u
            LEFT JOIN administrative_unit w ON w.id = u.ward_id
            LEFT JOIN administrative_unit p ON p.id = w.parent_id
            WHERE u.id = :id AND u.deleted_at IS NULL
            """, nativeQuery = true)
    Optional<ProfileView> findProfileViewById(@Param("id") UUID id);

    /** Chỉ xoá mềm user chưa xoá → gọi lại trả 0 (idempotent). */
    @Modifying
    @Query(value = """
            UPDATE "user" SET deleted_at = :now, updated_at = :now
            WHERE id = :id AND deleted_at IS NULL
            """, nativeQuery = true)
    int softDelete(@Param("id") UUID id, @Param("now") LocalDateTime now);

    /** Cộng/trừ điểm uy tín (chặn [0, 100]); {@code breaksCleanStreak} đưa chuỗi trận sạch về 0. 0 dòng = không có user. */
    @Modifying
    @Query(value = """
            UPDATE "user" SET reliability_score = LEAST(100, GREATEST(0, reliability_score + :delta)),
                              clean_streak = CASE WHEN :breaksCleanStreak THEN 0 ELSE clean_streak END
            WHERE id = :id
            """, nativeQuery = true)
    int adjustReliability(@Param("id") UUID id, @Param("delta") int delta,
                          @Param("breaksCleanStreak") boolean breaksCleanStreak);

    /** Phường / tỉnh trong hồ sơ + cờ đã xoá; rỗng = không có user. */
    @Query(value = """
            SELECT u.ward_id AS wardId, w.parent_id AS provinceId, (u.deleted_at IS NOT NULL) AS deleted
            FROM "user" u LEFT JOIN administrative_unit w ON w.id = u.ward_id
            WHERE u.id = :userId
            """, nativeQuery = true)
    Optional<ProfileAreaView> findProfileArea(@Param("userId") UUID userId);

    /** Uy tín cá nhân: điểm, huy hiệu, thống kê tính khi đọc (một câu). */
    @Query(value = """
            SELECT su.id AS userId, (su.deleted_at IS NOT NULL) AS deleted, su.reliability_score AS reliabilityScore,
                   su.verified_at AS verifiedAt, s.completed_matches AS completedMatches,
                   s.counted_matches AS countedMatches, s.avg_rating AS averageRating, s.rating_count AS ratingCount
            FROM "user" su CROSS JOIN LATERAL (""" + MercenarySql.USER_STATS_LATERAL + """
            ) s
            WHERE su.id = :userId
            """, nativeQuery = true)
    Optional<UserReputationView> findReputation(@Param("userId") UUID userId, @Param("now") LocalDateTime now,
                                                @Param("cancelWindowHours") int cancelWindowHours);

    /**
     * Mercenary hoàn thành trận (được đội trưởng chấm sao): khoá dòng user, +điểm hoàn thành + thưởng sao, chuỗi trận
     * sạch +1 nếu không có report cá nhân chưa bị bác trong trận (ngược lại về 0), thưởng mốc 5 / 10 và reset bộ đếm report
     * khi chạm mốc. Trả điểm trước / sau.
     */
    @Query(value = """
            WITH locked AS (
                SELECT u.id, u.reliability_score AS old_score, u.clean_streak AS old_streak
                FROM "user" u WHERE u.id = :userId FOR NO KEY UPDATE),
            calc AS (
                SELECT l.id, l.old_score, l.old_streak + 1 AS next_streak,
                       NOT EXISTS (SELECT 1 FROM report r
                                   WHERE r.reported_type = 'INDIVIDUAL' AND r.reported_id = l.id
                                     AND r.status <> 'REJECTED'
                                     AND (r.match_id = CAST(:matchId AS uuid)
                                          OR r.external_match_id = CAST(:externalMatchId AS uuid))) AS clean
                FROM locked l)
            UPDATE "user" u SET
                clean_streak = CASE WHEN c.clean THEN c.next_streak ELSE 0 END,
                reliability_score = LEAST(100, GREATEST(0, u.reliability_score + :completionPoints + :ratingBonus
                    + CASE WHEN c.clean AND c.next_streak % (2 * :milestone) = 0 THEN :bonus10
                           WHEN c.clean AND c.next_streak % :milestone = 0 THEN :bonus5 ELSE 0 END)),
                report_count_noshow = CASE WHEN c.clean AND c.next_streak % :milestone = 0 THEN 0
                                           ELSE u.report_count_noshow END,
                report_count_bad_play = CASE WHEN c.clean AND c.next_streak % :milestone = 0 THEN 0
                                             ELSE u.report_count_bad_play END,
                report_count_bad_communication = CASE WHEN c.clean AND c.next_streak % :milestone = 0 THEN 0
                                                      ELSE u.report_count_bad_communication END,
                report_count_skill_mismatch = CASE WHEN c.clean AND c.next_streak % :milestone = 0 THEN 0
                                                   ELSE u.report_count_skill_mismatch END
            FROM calc c WHERE u.id = c.id
            RETURNING 'USER' AS subjectType, u.id AS subjectId, c.old_score AS oldScore, u.reliability_score AS newScore
            """, nativeQuery = true)
    Optional<ScoreChangeView> applyMercenaryCompletion(@Param("userId") UUID userId, @Param("matchId") UUID matchId,
                                                       @Param("externalMatchId") UUID externalMatchId,
                                                       @Param("ratingBonus") int ratingBonus,
                                                       @Param("completionPoints") int completionPoints,
                                                       @Param("milestone") int milestone, @Param("bonus5") int bonus5,
                                                       @Param("bonus10") int bonus10);

    /**
     * Report cá nhân: tăng bộ đếm loại {@code type}, chuỗi về 0, trừ điểm theo bậc (bộ đếm sau khi tăng, chặn bậc 4),
     * chặn dưới 0. Trả điểm trước / sau.
     */
    @Query(value = """
            WITH locked AS (SELECT id, reliability_score AS old_score FROM "user" WHERE id = :id FOR NO KEY UPDATE)
            UPDATE "user" u SET
              report_count_noshow = CASE WHEN :type = 'NOSHOW' THEN u.report_count_noshow + 1 ELSE u.report_count_noshow END,
              report_count_bad_play = CASE WHEN :type = 'BAD_PLAY' THEN u.report_count_bad_play + 1
                                           ELSE u.report_count_bad_play END,
              report_count_bad_communication = CASE WHEN :type = 'BAD_COMMUNICATION'
                                           THEN u.report_count_bad_communication + 1
                                           ELSE u.report_count_bad_communication END,
              report_count_skill_mismatch = CASE WHEN :type = 'SKILL_MISMATCH' THEN u.report_count_skill_mismatch + 1
                                           ELSE u.report_count_skill_mismatch END,
              clean_streak = 0,
              reliability_score = GREATEST(0, u.reliability_score - (CAST(:tiers AS int[]))[LEAST(
                  CASE :type WHEN 'NOSHOW' THEN u.report_count_noshow
                             WHEN 'BAD_PLAY' THEN u.report_count_bad_play
                             WHEN 'BAD_COMMUNICATION' THEN u.report_count_bad_communication
                             ELSE u.report_count_skill_mismatch END + 1, 4)])
            FROM locked l WHERE u.id = l.id
            RETURNING 'USER' AS subjectType, u.id AS subjectId, l.old_score AS oldScore,
                      u.reliability_score AS newScore
            """, nativeQuery = true)
    Optional<ScoreChangeView> applyReportPenalty(@Param("id") UUID id, @Param("type") String type,
                                                 @Param("tiers") int[] tiers);

    /** Hoàn điểm khi report cá nhân bị bác: cộng {@code points} (chặn 100), giảm bộ đếm loại đó (chặn 0). */
    @Modifying
    @Query(value = """
            UPDATE "user" SET
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
     * Cấp / thu hồi huy hiệu cá nhân theo điều kiện hiện tại (có số điện thoại, chưa xoá, số trận hoàn thành, sao trung
     * bình, điểm); chỉ ghi dòng đổi trạng thái → trả người vừa đổi.
     */
    @Query(value = """
            UPDATE "user" t SET verified_at = CASE WHEN e.eligible THEN :now ELSE NULL END
            FROM (SELECT su.id,
                         COALESCE(su.phone_number IS NOT NULL AND su.deleted_at IS NULL
                                  AND s.completed_matches > :minCompleted AND s.avg_rating > :minAvg
                                  AND su.reliability_score >= :minScore, false) AS eligible
                  FROM "user" su CROSS JOIN LATERAL (""" + MercenarySql.USER_STATS_LATERAL + """
                  ) s WHERE su.id = ANY(CAST(:ids AS uuid[]))) e
            WHERE t.id = e.id AND (t.verified_at IS NOT NULL) IS DISTINCT FROM e.eligible
            RETURNING t.id AS subjectId, (t.verified_at IS NOT NULL) AS verified
            """, nativeQuery = true)
    List<VerificationChangeView> refreshVerification(@Param("ids") UUID[] ids, @Param("now") LocalDateTime now,
                                                     @Param("cancelWindowHours") int cancelWindowHours,
                                                     @Param("minCompleted") int minCompleted,
                                                     @Param("minAvg") double minAvg, @Param("minScore") int minScore);

    /** Job đối soát huy hiệu cá nhân: lô id tăng dần sau {@code afterId} (null = từ đầu). */
    @Query(value = """
            SELECT u.id FROM "user" u
            WHERE CAST(:afterId AS uuid) IS NULL OR u.id > CAST(:afterId AS uuid)
            ORDER BY u.id LIMIT :limit
            """, nativeQuery = true)
    List<UUID> findIdsForVerification(@Param("afterId") UUID afterId, @Param("limit") int limit);
}
