package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.entity.venue.VenueReport;
import com.phananh.arenaflow.domain.projection.AdminVenueReportView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/** Truy cập bảng {@code venue_report}: danh sách report cho Admin, xử lý report có điều kiện theo trạng thái. */
public interface VenueReportRepository extends JpaRepository<VenueReport, UUID> {

    /** {@code status}/{@code venueId} null = không lọc. Sắp xếp cố định: mới nhất trước, sau đó id giảm dần. */
    @Query(value = """
            SELECT r.id AS reportId, r.venue_id AS venueId, v.name AS venueName, v.status AS venueStatus,
                   r.reporter_id AS reporterId, ru.display_name AS reporterName,
                   (ru.deleted_at IS NOT NULL) AS reporterDeleted, r.reason AS reason, r.detail AS detail,
                   r.status AS status, r.handled_by AS handledById, hu.display_name AS handledByName,
                   (hu.deleted_at IS NOT NULL) AS handledByDeleted, r.handled_at AS handledAt, r.created_at AS createdAt
            FROM venue_report r
            JOIN venue v ON v.id = r.venue_id
            JOIN "user" ru ON ru.id = r.reporter_id
            LEFT JOIN "user" hu ON hu.id = r.handled_by
            WHERE (CAST(:status AS VARCHAR) IS NULL OR r.status = CAST(:status AS VARCHAR))
              AND (CAST(:venueId AS UUID) IS NULL OR r.venue_id = CAST(:venueId AS UUID))
            ORDER BY r.created_at DESC, r.id DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM venue_report r
            WHERE (CAST(:status AS VARCHAR) IS NULL OR r.status = CAST(:status AS VARCHAR))
              AND (CAST(:venueId AS UUID) IS NULL OR r.venue_id = CAST(:venueId AS UUID))
            """,
            nativeQuery = true)
    Page<AdminVenueReportView> findAdminReports(@Param("status") String status, @Param("venueId") UUID venueId,
                                                Pageable pageable);

    @Query(value = """
            SELECT r.id AS reportId, r.venue_id AS venueId, v.name AS venueName, v.status AS venueStatus,
                   r.reporter_id AS reporterId, ru.display_name AS reporterName,
                   (ru.deleted_at IS NOT NULL) AS reporterDeleted, r.reason AS reason, r.detail AS detail,
                   r.status AS status, r.handled_by AS handledById, hu.display_name AS handledByName,
                   (hu.deleted_at IS NOT NULL) AS handledByDeleted, r.handled_at AS handledAt, r.created_at AS createdAt
            FROM venue_report r
            JOIN venue v ON v.id = r.venue_id
            JOIN "user" ru ON ru.id = r.reporter_id
            LEFT JOIN "user" hu ON hu.id = r.handled_by
            WHERE r.id = :reportId
            """, nativeQuery = true)
    Optional<AdminVenueReportView> findAdminViewById(@Param("reportId") UUID reportId);

    /** Xử lý có điều kiện: 0 dòng khi admin khác hoặc thao tác đánh dấu ngừng hoạt động đã xử lý report. */
    @Modifying
    @Query(value = "UPDATE venue_report SET status = :status, handled_by = :adminId, handled_at = :now "
            + "WHERE id = :reportId AND status = 'PENDING'", nativeQuery = true)
    int resolveIfPending(@Param("reportId") UUID reportId, @Param("status") String status,
                         @Param("adminId") UUID adminId, @Param("now") LocalDateTime now);

    /**
     * Admin đánh dấu địa điểm ngừng hoạt động: mọi report CLOSED còn PENDING của địa điểm → VERIFIED, ghi người và
     * thời điểm xử lý. Trả số report đã cập nhật (0 khi không còn report chờ).
     */
    @Modifying
    @Query(value = "UPDATE venue_report SET status = 'VERIFIED', handled_by = :adminId, handled_at = :now "
            + "WHERE venue_id = :venueId AND reason = 'CLOSED' AND status = 'PENDING'", nativeQuery = true)
    int verifyPendingClosureReports(@Param("venueId") UUID venueId, @Param("adminId") UUID adminId,
                                    @Param("now") LocalDateTime now);
}
