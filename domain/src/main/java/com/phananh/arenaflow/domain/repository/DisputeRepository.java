package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.entity.rating.Dispute;
import com.phananh.arenaflow.domain.projection.AdminDisputeDetailView;
import com.phananh.arenaflow.domain.projection.AdminDisputeView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DisputeRepository extends JpaRepository<Dispute, UUID> {

    /**
     * Cột tranh chấp cho Admin: tên thật người dùng + cờ đã xoá (API Admin không che tên), hai đội kèm trạng thái.
     * Chỉ tranh chấp report giữa hai đội.
     */
    String ADMIN_COLUMNS = """
            SELECT d.id AS disputeId, r.id AS reportId, r.match_id AS matchId, m.match_start_time AS matchStartTime,
                   rt.id AS reporterTeamId, rt.name AS reporterTeamName, rt.avatar_url AS reporterTeamAvatarUrl,
                   rt.status AS reporterTeamStatus,
                   dt.id AS reportedTeamId, dt.name AS reportedTeamName, dt.avatar_url AS reportedTeamAvatarUrl,
                   dt.status AS reportedTeamStatus,
                   ru.id AS reportedById, ru.display_name AS reportedByName, (ru.deleted_at IS NOT NULL) AS reportedByDeleted,
                   du.id AS disputedById, du.display_name AS disputedByName, (du.deleted_at IS NOT NULL) AS disputedByDeleted,
                   d.status AS status, d.decision AS decision,
                   au.id AS resolvedById, au.display_name AS resolvedByName, (au.deleted_at IS NOT NULL) AS resolvedByDeleted,
                   d.resolved_at AS resolvedAt, r.created_at AS reportCreatedAt, d.created_at AS createdAt
            """;

    String ADMIN_JOINS = """
            FROM dispute d
            JOIN report r ON r.id = d.report_id
            JOIN match m ON m.id = r.match_id
            JOIN team rt ON rt.id = r.reporter_id
            JOIN team dt ON dt.id = r.reported_id
            JOIN "user" ru ON ru.id = r.created_by_user_id
            JOIN "user" du ON du.id = d.created_by_user_id
            LEFT JOIN "user" au ON au.id = d.resolved_by_user_id
            """;

    String TEAM_DISPUTE_FILTER = """
            WHERE r.reporter_type = 'TEAM' AND r.reported_type = 'TEAM'
              AND (CAST(:status AS varchar) IS NULL OR d.status = CAST(:status AS varchar))
            """;

    /**
     * PENDING → RESOLVED có điều kiện, ghi quyết định / ghi chú / Admin / thời điểm; trả {@code report_id}. Rỗng =
     * không có tranh chấp hoặc đã được xử lý. Không {@code @Modifying} vì câu trả về dòng.
     */
    @Query(value = """
            UPDATE dispute SET status = 'RESOLVED', decision = CAST(:decision AS varchar), resolution_note = :note,
                               resolved_by_user_id = :adminId, resolved_at = :now, updated_at = :now
            WHERE id = :id AND status = 'PENDING'
            RETURNING report_id
            """, nativeQuery = true)
    Optional<UUID> resolveIfPending(@Param("id") UUID disputeId, @Param("decision") String decision,
                                    @Param("note") String note, @Param("adminId") UUID adminId,
                                    @Param("now") LocalDateTime now);

    /** Hàng chờ xử lý theo thứ tự đến: cũ nhất trước (index trạng thái + thời điểm). {@code status} null = mọi trạng thái. */
    @Query(value = ADMIN_COLUMNS + ADMIN_JOINS + TEAM_DISPUTE_FILTER + """
            ORDER BY d.created_at ASC, d.id ASC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<AdminDisputeView> findAdminDisputesOldestFirst(@Param("status") String status, @Param("limit") int limit,
                                                        @Param("offset") long offset);

    /** Mới nhất trước. {@code status} null = mọi trạng thái. */
    @Query(value = ADMIN_COLUMNS + ADMIN_JOINS + TEAM_DISPUTE_FILTER + """
            ORDER BY d.created_at DESC, d.id DESC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<AdminDisputeView> findAdminDisputesNewestFirst(@Param("status") String status, @Param("limit") int limit,
                                                        @Param("offset") long offset);

    @Query(value = """
            SELECT COUNT(*) FROM dispute d JOIN report r ON r.id = d.report_id
            """ + TEAM_DISPUTE_FILTER, nativeQuery = true)
    long countAdminDisputes(@Param("status") String status);

    /** Chi tiết cho Admin: cột danh sách + môn, trận, sân, lý do report, bằng chứng, quyết định, điểm hiện tại hai đội. */
    @Query(value = ADMIN_COLUMNS + """
            , s.name AS sportName, m.status AS matchStatus, m.match_end_time AS matchEndTime, v.name AS venueName,
              r.reason AS reportReason, r.reliability_penalty AS reliabilityPenalty, d.evidence AS evidence,
              d.resolution_note AS resolutionNote, rt.reliability_score AS reporterTeamReliabilityScore,
              dt.reliability_score AS reportedTeamReliabilityScore
            """ + ADMIN_JOINS + """
            JOIN sport s ON s.id = m.sport_id
            LEFT JOIN venue v ON v.id = m.venue_id
            WHERE d.id = :id AND r.reporter_type = 'TEAM' AND r.reported_type = 'TEAM'
            """, nativeQuery = true)
    Optional<AdminDisputeDetailView> findAdminDisputeDetail(@Param("id") UUID disputeId);
}
