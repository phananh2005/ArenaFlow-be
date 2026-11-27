package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.entity.mercenary.MercenaryApplication;
import com.phananh.arenaflow.domain.projection.ApplicantRowView;
import com.phananh.arenaflow.domain.projection.ApplicationContextView;
import com.phananh.arenaflow.domain.projection.ApplicationView;
import com.phananh.arenaflow.domain.projection.ApplyContextView;
import com.phananh.arenaflow.domain.projection.GameCancelNoticeView;
import com.phananh.arenaflow.domain.projection.OrphanRejectedApplicationView;
import com.phananh.arenaflow.domain.projection.RejectedApplicationView;
import com.phananh.arenaflow.domain.projection.WithdrawnApplicationView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Đơn ứng tuyển mercenary. Đổi trạng thái chỉ bằng câu UPDATE có điều kiện trạng thái (CAS); câu {@code … RETURNING}
 * khai native trả projection, không {@code @Modifying}. Mọi câu ghi đơn chạy sau câu khoá tin (thứ tự tin → đơn).
 */
public interface MercenaryApplicationRepository extends JpaRepository<MercenaryApplication, UUID> {

    /**
     * Context ứng tuyển xuất phát từ người gọi: luôn có một dòng; tin không có → {@code recruitmentId} null. Gồm cờ
     * người gọi đã xoá, trạng thái hiệu lực của tin, đội tuyển, trận, khu vực, người gọi là thành viên đội trong trận,
     * đã có đơn đang hoạt động ở tin, đã có đơn APPROVED trùng giờ.
     */
    @Query(value = "SELECT (me.deleted_at IS NOT NULL) AS callerDeleted, me.display_name AS callerName,"
            + " me.avatar_url AS callerAvatarUrl, me.preferred_position AS callerPreferredPosition,"
            + " r.id AS recruitmentId, r.status AS status, CASE WHEN r.id IS NULL THEN NULL ELSE "
            + MercenarySql.RECRUITMENT_EFFECTIVE_STATUS + " END AS effectiveStatus,"
            + " r.team_id AS teamId, r.match_id AS matchId, r.external_match_id AS externalMatchId,"
            + " r.position AS position, r.quantity_needed AS quantityNeeded, r.quantity_approved AS quantityApproved,"
            + " t.status AS teamStatus, t.name AS teamName, t.avatar_url AS teamAvatarUrl,"
            + " CASE WHEN r.id IS NULL THEN NULL ELSE " + MercenarySql.GAME_SOURCE + " END AS source,"
            + " x.opponent_name AS opponentName, " + MercenarySql.GAME_STATUS + " AS gameStatus, "
            + MercenarySql.GAME_START + " AS gameStart, " + MercenarySql.GAME_END + " AS gameEnd,"
            + " vw.name AS wardName, vp.name AS provinceName, "
            + MercenarySql.MEMBER_OF_GAME_TEAMS_CALLER + " AS callerMember,"
            + " EXISTS (SELECT 1 FROM mercenary_application ea WHERE ea.recruitment_id = r.id"
            + " AND ea.applicant_id = :callerId AND ea.status IN ('PENDING', 'APPROVED')) AS callerHasActive, "
            + MercenarySql.OVERLAPPING_APPROVAL_OF_CALLER + " AS callerOverlap" + """

            FROM "user" me
            LEFT JOIN mercenary_recruitment r ON r.id = :recruitmentId
            LEFT JOIN match m ON m.id = r.match_id
            LEFT JOIN external_match x ON x.id = r.external_match_id
            LEFT JOIN team t ON t.id = r.team_id
            LEFT JOIN venue v ON v.id = COALESCE(m.venue_id, x.venue_id)
            LEFT JOIN administrative_unit vw ON vw.id = v.ward_id
            LEFT JOIN administrative_unit vp ON vp.id = vw.parent_id
            WHERE me.id = :callerId
            """, nativeQuery = true)
    Optional<ApplyContextView> findApplyContext(@Param("recruitmentId") UUID recruitmentId,
                                                @Param("callerId") UUID callerId, @Param("now") LocalDateTime now);

    /**
     * Context thao tác trên một đơn: dữ liệu hiển thị + cờ người gọi (đội trưởng ACTIVE hiện tại / ứng viên) + ứng viên
     * là thành viên đội trong trận + ứng viên có đơn APPROVED trùng giờ + trận huỷ / đã kết thúc.
     */
    @Query(value = "SELECT " + MercenarySql.APPLICATION_COLUMNS + ","
            + " COALESCE(cu.id = :callerId, false) AS callerCaptain, (a.applicant_id = :callerId) AS callerApplicant, "
            + MercenarySql.MEMBER_OF_GAME_TEAMS_APPLICANT + " AS applicantMember, "
            + MercenarySql.OVERLAPPING_APPROVAL_OF_APPLICANT + " AS applicantOverlap, "
            + MercenarySql.GAME_CANCELLED + " AS gameCancelled, " + MercenarySql.GAME_ENDED + " AS gameEnded"
            + MercenarySql.APPLICATION_FROM + " WHERE a.id = :id", nativeQuery = true)
    Optional<ApplicationContextView> findContext(@Param("id") UUID id, @Param("callerId") UUID callerId,
                                                 @Param("now") LocalDateTime now);

    /**
     * Ứng viên của tin, lọc trên trạng thái hiệu lực (null = tất cả), kèm uy tín cá nhân tính trong cùng câu. Sắp xếp:
     * nộp sớm trước, rồi id.
     */
    @Query(value = "SELECT * FROM (SELECT a.id AS applicationId, " + MercenarySql.APPLICATION_EFFECTIVE_STATUS
            + " AS effectiveStatus, c.id AS conversationId, su.id AS userId, su.display_name AS displayName,"
            + " su.avatar_url AS avatarUrl, (su.deleted_at IS NOT NULL) AS deleted,"
            + " su.preferred_position AS preferredPosition, su.reliability_score AS reliabilityScore,"
            + " su.verified_at AS verifiedAt, s.completed_matches AS completedMatches,"
            + " s.counted_matches AS countedMatches, s.avg_rating AS averageRating, s.rating_count AS ratingCount,"
            + " a.applied_at AS appliedAt, a.responded_at AS respondedAt, a.withdrawn_at AS withdrawnAt" + """

            FROM mercenary_application a
            JOIN mercenary_recruitment r ON r.id = a.recruitment_id
            LEFT JOIN match m ON m.id = r.match_id
            LEFT JOIN external_match x ON x.id = r.external_match_id
            JOIN "user" su ON su.id = a.applicant_id
            LEFT JOIN conversation c ON c.application_id = a.id
            CROSS JOIN LATERAL (""" + MercenarySql.USER_STATS_LATERAL + """
            ) s
            WHERE a.recruitment_id = :recruitmentId
            ) q
            WHERE CAST(:status AS varchar) IS NULL OR q.effectiveStatus = CAST(:status AS varchar)
            ORDER BY q.appliedAt ASC, q.applicationId ASC
            """, countQuery = "SELECT COUNT(*) FROM (SELECT " + MercenarySql.APPLICATION_EFFECTIVE_STATUS
            + " AS effectiveStatus" + """

            FROM mercenary_application a
            JOIN mercenary_recruitment r ON r.id = a.recruitment_id
            LEFT JOIN match m ON m.id = r.match_id
            LEFT JOIN external_match x ON x.id = r.external_match_id
            WHERE a.recruitment_id = :recruitmentId AND CAST(:cancelWindowHours AS int) IS NOT NULL
            ) q
            WHERE CAST(:status AS varchar) IS NULL OR q.effectiveStatus = CAST(:status AS varchar)
            """, nativeQuery = true)
    Page<ApplicantRowView> findApplicants(@Param("recruitmentId") UUID recruitmentId, @Param("status") String status,
                                          @Param("now") LocalDateTime now,
                                          @Param("cancelWindowHours") int cancelWindowHours, Pageable pageable);

    /**
     * Đơn của một ứng viên, lọc trên trạng thái hiệu lực: {@code ACTIVE} = PENDING + APPROVED, null = tất cả. Sắp xếp:
     * nộp mới nhất trước, rồi id giảm dần.
     */
    @Query(value = "SELECT * FROM (SELECT " + MercenarySql.APPLICATION_COLUMNS + MercenarySql.APPLICATION_FROM + """
             WHERE a.applicant_id = :applicantId
            ) q
            WHERE CAST(:status AS varchar) IS NULL
               OR (CAST(:status AS varchar) = 'ACTIVE' AND q.effectiveStatus IN ('PENDING', 'APPROVED'))
               OR q.effectiveStatus = CAST(:status AS varchar)
            ORDER BY q.appliedAt DESC, q.applicationId DESC
            """, countQuery = "SELECT COUNT(*) FROM (SELECT " + MercenarySql.APPLICATION_EFFECTIVE_STATUS
            + " AS effectiveStatus" + """

            FROM mercenary_application a
            JOIN mercenary_recruitment r ON r.id = a.recruitment_id
            LEFT JOIN match m ON m.id = r.match_id
            LEFT JOIN external_match x ON x.id = r.external_match_id
            WHERE a.applicant_id = :applicantId
            ) q
            WHERE CAST(:status AS varchar) IS NULL
               OR (CAST(:status AS varchar) = 'ACTIVE' AND q.effectiveStatus IN ('PENDING', 'APPROVED'))
               OR q.effectiveStatus = CAST(:status AS varchar)
            """, nativeQuery = true)
    Page<ApplicationView> findByApplicant(@Param("applicantId") UUID applicantId, @Param("status") String status,
                                          @Param("now") LocalDateTime now, Pageable pageable);

    /** PENDING → APPROVED; 0 dòng = đơn vừa đổi trạng thái. */
    @Modifying
    @Query(value = """
            UPDATE mercenary_application SET status = 'APPROVED', responded_at = :now
            WHERE id = :id AND status = 'PENDING'
            """, nativeQuery = true)
    int approveIfPending(@Param("id") UUID id, @Param("now") LocalDateTime now);

    /** PENDING → REJECTED; 0 dòng = đơn vừa đổi trạng thái. */
    @Modifying
    @Query(value = """
            UPDATE mercenary_application SET status = 'REJECTED', responded_at = :now
            WHERE id = :id AND status = 'PENDING'
            """, nativeQuery = true)
    int rejectIfPending(@Param("id") UUID id, @Param("now") LocalDateTime now);

    /** Rút đơn chờ; 0 dòng = đơn vừa đổi trạng thái. */
    @Modifying
    @Query(value = """
            UPDATE mercenary_application SET status = 'WITHDRAWN', withdrawn_at = :now
            WHERE id = :id AND status = 'PENDING'
            """, nativeQuery = true)
    int withdrawPending(@Param("id") UUID id, @Param("now") LocalDateTime now);

    /** Rút lui sau duyệt (giữ {@code responded_at} để thống kê phân biệt); 0 dòng = đơn vừa đổi trạng thái. */
    @Modifying
    @Query(value = """
            UPDATE mercenary_application SET status = 'WITHDRAWN', withdrawn_at = :now
            WHERE id = :id AND status = 'APPROVED'
            """, nativeQuery = true)
    int withdrawApproved(@Param("id") UUID id, @Param("now") LocalDateTime now);

    /** Đóng tin: từ chối mọi đơn chờ của tin (chạy sau câu khoá tin); trả id đơn bị từ chối. */
    @Query(value = """
            UPDATE mercenary_application SET status = 'REJECTED', responded_at = :now
            WHERE recruitment_id = :recruitmentId AND status = 'PENDING'
            RETURNING id
            """, nativeQuery = true)
    List<UUID> rejectPendingByRecruitment(@Param("recruitmentId") UUID recruitmentId,
                                          @Param("now") LocalDateTime now);

    /** Như trên cho nhiều tin (huỷ trận ngoài, job). */
    @Query(value = """
            UPDATE mercenary_application SET status = 'REJECTED', responded_at = :now
            WHERE recruitment_id = ANY(CAST(:recruitmentIds AS uuid[])) AND status = 'PENDING'
            RETURNING id AS id, recruitment_id AS recruitmentId
            """, nativeQuery = true)
    List<RejectedApplicationView> rejectPendingByRecruitmentIds(@Param("recruitmentIds") UUID[] recruitmentIds,
                                                                @Param("now") LocalDateTime now);

    /** Job: đơn PENDING còn sót trên tin đã CLOSED/EXPIRED (ứng tuyển commit sau khi tin đóng) → REJECTED theo lô. */
    @Query(value = """
            UPDATE mercenary_application a SET status = 'REJECTED', responded_at = :now
            FROM mercenary_recruitment rr
            WHERE rr.id = a.recruitment_id
              AND a.id IN (SELECT a2.id FROM mercenary_application a2
                           JOIN mercenary_recruitment r ON r.id = a2.recruitment_id
                           WHERE a2.status = 'PENDING' AND r.status IN ('CLOSED', 'EXPIRED')
                           ORDER BY a2.id LIMIT :limit
                           FOR NO KEY UPDATE OF a2 SKIP LOCKED)
            RETURNING a.id AS id, a.recruitment_id AS recruitmentId, a.applicant_id AS applicantId,
                      rr.match_id AS matchId, rr.external_match_id AS externalMatchId, rr.team_id AS teamId
            """, nativeQuery = true)
    List<OrphanRejectedApplicationView> rejectOrphanPending(@Param("now") LocalDateTime now,
                                                            @Param("limit") int limit);

    /**
     * Job: đánh dấu đã báo huỷ trận cho đơn APPROVED của kèo / trận ngoài bị huỷ có giờ kết thúc sau
     * {@code lookbackStart}. Hai nhánh UNION ALL, mỗi nhánh join đúng một cột khoá trận; khoá ở tầng ngoài (PostgreSQL
     * không cho khoá cùng tầng UNION) và kiểm lại điều kiện sau khi khoá.
     */
    @Query(value = """
            UPDATE mercenary_application a SET game_cancel_notified_at = :now
            FROM mercenary_recruitment r
            WHERE a.recruitment_id = r.id
              AND a.id IN (
                SELECT a2.id FROM mercenary_application a2
                WHERE a2.id IN (
                    SELECT a3.id FROM match m
                    JOIN mercenary_recruitment r3 ON r3.match_id = m.id
                    JOIN mercenary_application a3 ON a3.recruitment_id = r3.id
                    WHERE m.status = 'CANCELLED' AND m.match_end_time > :lookbackStart
                      AND r3.match_id IS NOT NULL
                      AND a3.status = 'APPROVED' AND a3.game_cancel_notified_at IS NULL
                    UNION ALL
                    SELECT a3.id FROM external_match x
                    JOIN mercenary_recruitment r3 ON r3.external_match_id = x.id
                    JOIN mercenary_application a3 ON a3.recruitment_id = r3.id
                    WHERE x.status = 'CANCELLED' AND x.match_end_time > :lookbackStart
                      AND r3.external_match_id IS NOT NULL
                      AND a3.status = 'APPROVED' AND a3.game_cancel_notified_at IS NULL)
                  AND a2.status = 'APPROVED' AND a2.game_cancel_notified_at IS NULL
                ORDER BY a2.id LIMIT :limit
                FOR NO KEY UPDATE OF a2 SKIP LOCKED)
            RETURNING a.id AS id, r.match_id AS matchId, r.external_match_id AS externalMatchId
            """, nativeQuery = true)
    List<GameCancelNoticeView> markGameCancelNotified(@Param("now") LocalDateTime now,
                                                      @Param("lookbackStart") LocalDateTime lookbackStart,
                                                      @Param("limit") int limit);

    /** Huỷ trận ngoài: đánh dấu đã báo cho mọi đơn APPROVED của mọi tin của trận (kể cả tin đã đóng trước đó). */
    @Query(value = """
            UPDATE mercenary_application a SET game_cancel_notified_at = :now
            FROM mercenary_recruitment r
            WHERE a.recruitment_id = r.id AND r.external_match_id = :externalMatchId
              AND a.status = 'APPROVED' AND a.game_cancel_notified_at IS NULL
            RETURNING a.id AS id, r.match_id AS matchId, r.external_match_id AS externalMatchId
            """, nativeQuery = true)
    List<GameCancelNoticeView> markGameCancelNotifiedByExternalMatch(@Param("externalMatchId") UUID externalMatchId,
                                                                     @Param("now") LocalDateTime now);

    /**
     * Xoá tài khoản ứng viên: đơn PENDING/APPROVED của trận đã xác nhận chưa bắt đầu → WITHDRAWN (chạy sau câu bớt số
     * đã duyệt của tin). {@code wasApproved} dựa trên {@code responded_at} (đơn chờ chưa có).
     */
    @Query(value = """
            UPDATE mercenary_application a SET status = 'WITHDRAWN', withdrawn_at = :now
            FROM mercenary_recruitment r
            LEFT JOIN match m ON m.id = r.match_id
            LEFT JOIN external_match x ON x.id = r.external_match_id
            WHERE a.recruitment_id = r.id AND a.applicant_id = :userId AND a.status IN ('PENDING', 'APPROVED')
              AND COALESCE(m.status, x.status) IN ('CONFIRMED', 'SCHEDULED')
              AND COALESCE(m.match_start_time, x.match_start_time) > :now
            RETURNING a.id AS id, a.recruitment_id AS recruitmentId, r.team_id AS teamId, r.match_id AS matchId,
                      r.external_match_id AS externalMatchId, (a.responded_at IS NOT NULL) AS wasApproved
            """, nativeQuery = true)
    List<WithdrawnApplicationView> withdrawActiveOfApplicant(@Param("userId") UUID userId,
                                                             @Param("now") LocalDateTime now);
}
