package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.entity.rating.Dispute;
import com.phananh.arenaflow.domain.projection.AdminMercenaryDisputeDetailView;
import com.phananh.arenaflow.domain.projection.AdminMercenaryDisputeView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tranh chấp report bùng. Hàng chờ đội ↔ đội và hàng chờ liên quan mercenary tách theo {@code report.application_id}.
 */
public interface DisputeRepository extends JpaRepository<Dispute, UUID> {

    String ADMIN_MERCENARY_SELECT = """
            SELECT d.id AS disputeId, rp.id AS reportId, rp.application_id AS applicationId, rc.match_id AS matchId,
                   rc.external_match_id AS externalMatchId,
                   COALESCE(m.match_start_time, x.match_start_time) AS gameStart, rp.reporter_type AS reporterType,
                   t.id AS teamId, t.name AS teamName, t.avatar_url AS teamAvatarUrl, t.status AS teamStatus,
                   mu.id AS mercenaryId, mu.display_name AS mercenaryName, (mu.deleted_at IS NOT NULL) AS mercenaryDeleted,
                   ru.id AS reportedById, ru.display_name AS reportedByName, (ru.deleted_at IS NOT NULL) AS reportedByDeleted,
                   du.id AS disputedById, du.display_name AS disputedByName, (du.deleted_at IS NOT NULL) AS disputedByDeleted,
                   d.status AS status, d.decision AS decision,
                   au.id AS resolvedById, au.display_name AS resolvedByName,
                   COALESCE(au.deleted_at IS NOT NULL, false) AS resolvedByDeleted, d.resolved_at AS resolvedAt,
                   rp.created_at AS reportCreatedAt, d.created_at AS createdAt
            """;

    String ADMIN_MERCENARY_FROM = """
             FROM dispute d
             JOIN report rp ON rp.id = d.report_id AND rp.application_id IS NOT NULL
             JOIN mercenary_application a ON a.id = rp.application_id
             JOIN mercenary_recruitment rc ON rc.id = a.recruitment_id
             LEFT JOIN match m ON m.id = rc.match_id
             LEFT JOIN external_match x ON x.id = rc.external_match_id
             JOIN team t ON t.id = rc.team_id
             JOIN "user" mu ON mu.id = a.applicant_id
             JOIN "user" ru ON ru.id = rp.created_by_user_id
             JOIN "user" du ON du.id = d.created_by_user_id
             LEFT JOIN "user" au ON au.id = d.resolved_by_user_id
            """;

    /**
     * Chốt tranh chấp liên quan mercenary đang PENDING (CAS); rỗng = không có / không thuộc hàng chờ mercenary / đã xử lý.
     */
    @Query(value = """
            UPDATE dispute SET status = 'RESOLVED', decision = :decision, resolution_note = :note,
                               resolved_by_user_id = :adminId, resolved_at = :now, updated_at = :now
            WHERE id = :id AND status = 'PENDING'
              AND EXISTS (SELECT 1 FROM report r WHERE r.id = dispute.report_id AND r.application_id IS NOT NULL)
            RETURNING report_id
            """, nativeQuery = true)
    Optional<UUID> resolveMercenaryIfPending(@Param("id") UUID id, @Param("decision") String decision,
                                             @Param("note") String note, @Param("adminId") UUID adminId,
                                             @Param("now") LocalDateTime now);

    /** Có tranh chấp thuộc hàng chờ mercenary với id này (phân biệt "không có" với "đã xử lý"). */
    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM dispute d JOIN report r ON r.id = d.report_id
                           WHERE d.id = :id AND r.application_id IS NOT NULL)
            """, nativeQuery = true)
    boolean existsMercenaryDispute(@Param("id") UUID id);

    /** Có tranh chấp thuộc hàng chờ đội ↔ đội với id này. */
    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM dispute d JOIN report r ON r.id = d.report_id
                           WHERE d.id = :id AND r.application_id IS NULL)
            """, nativeQuery = true)
    boolean existsTeamDispute(@Param("id") UUID id);

    /**
     * Hàng chờ tranh chấp mercenary: {@code status} null = tất cả; PENDING đọc cũ nhất trước, còn lại mới nhất trước.
     * Tên thật + cờ đã xoá (không che).
     */
    @Query(value = ADMIN_MERCENARY_SELECT + ADMIN_MERCENARY_FROM + """
             WHERE CAST(:status AS varchar) IS NULL OR d.status = CAST(:status AS varchar)
             ORDER BY CASE WHEN :oldestFirst THEN d.created_at END ASC,
                      CASE WHEN :oldestFirst THEN d.id END ASC,
                      d.created_at DESC, d.id DESC
             LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<AdminMercenaryDisputeView> findAdminMercenaryDisputes(@Param("status") String status,
                                                               @Param("oldestFirst") boolean oldestFirst,
                                                               @Param("limit") int limit, @Param("offset") long offset);

    @Query(value = """
            SELECT COUNT(*) FROM dispute d JOIN report rp ON rp.id = d.report_id AND rp.application_id IS NOT NULL
            WHERE CAST(:status AS varchar) IS NULL OR d.status = CAST(:status AS varchar)
            """, nativeQuery = true)
    long countAdminMercenaryDisputes(@Param("status") String status);

    /** Chi tiết tranh chấp mercenary cho Admin; rỗng = không có hoặc không thuộc hàng chờ mercenary. */
    @Query(value = ADMIN_MERCENARY_SELECT + """
                   , CASE WHEN rc.match_id IS NOT NULL THEN 'SYSTEM' ELSE 'EXTERNAL' END AS source,
                   s.name AS sportName, COALESCE(m.status, x.status) AS gameStatus,
                   COALESCE(m.match_end_time, x.match_end_time) AS gameEnd, v.name AS venueName,
                   x.opponent_name AS opponentName, rc.position AS position, rp.reason AS reportReason,
                   rp.reliability_penalty AS reliabilityPenalty, d.evidence AS evidence,
                   d.resolution_note AS resolutionNote, t.reliability_score AS teamReliabilityScore,
                   mu.reliability_score AS mercenaryReliabilityScore
            """ + ADMIN_MERCENARY_FROM + """
             LEFT JOIN sport s ON s.id = m.sport_id
             LEFT JOIN venue v ON v.id = COALESCE(m.venue_id, x.venue_id)
             WHERE d.id = :id
            """, nativeQuery = true)
    Optional<AdminMercenaryDisputeDetailView> findAdminMercenaryDisputeDetail(@Param("id") UUID id);
}
