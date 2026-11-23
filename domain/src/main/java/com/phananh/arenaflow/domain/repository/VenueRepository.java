package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.entity.venue.Venue;
import com.phananh.arenaflow.domain.projection.SelectableVenueView;
import com.phananh.arenaflow.domain.projection.StaleVenueView;
import com.phananh.arenaflow.domain.projection.VenueDetailView;
import com.phananh.arenaflow.domain.projection.VenueSummaryView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Truy cập bảng {@code venue}: tìm kiếm phân trang theo trạng thái/vị trí, khoá dòng và cập nhật cờ cảnh báo. */
public interface VenueRepository extends JpaRepository<Venue, UUID> {

    /** Lấy row lock PostgreSQL trước khi ghi ảnh và tăng version cho các writer CAS đồng thời. */
    @Modifying
    @Query(value = "UPDATE venue SET version = version + 1 WHERE id = :venueId", nativeQuery = true)
    int lockRow(@Param("venueId") UUID venueId);

    /** Mở cảnh báo đóng cửa (mỗi đợt một lần); 0 dòng khi đã cảnh báo hoặc địa điểm không còn ACTIVE. */
    @Modifying
    @Query(value = "UPDATE venue SET closure_alerted_at = :now, version = version + 1 "
            + "WHERE id = :venueId AND status = 'ACTIVE' AND closure_alerted_at IS NULL", nativeQuery = true)
    int markClosureAlerted(@Param("venueId") UUID venueId, @Param("now") LocalDateTime now);

    /** Đặt lại cảnh báo đóng cửa khi không còn report CLOSED đang chờ, để đợt sau lại cảnh báo. */
    @Modifying
    @Query(value = "UPDATE venue SET closure_alerted_at = NULL, version = version + 1 "
            + "WHERE id = :venueId AND closure_alerted_at IS NOT NULL AND NOT EXISTS (SELECT 1 FROM venue_report r "
            + "WHERE r.venue_id = :venueId AND r.reason = 'CLOSED' AND r.status = 'PENDING')", nativeQuery = true)
    int reopenClosureAlertIfNoPending(@Param("venueId") UUID venueId);

    @Query(value = """
            SELECT v.id AS id, v.name AS name, v.address AS address, v.latitude AS latitude, v.longitude AS longitude,
                   v.status AS status, vi.image_url AS primaryImageUrl, w.name AS wardName, p.name AS provinceName
            FROM venue v
            LEFT JOIN venue_image vi ON vi.venue_id = v.id AND vi.is_primary
            LEFT JOIN administrative_unit w ON w.id = v.ward_id
            LEFT JOIN administrative_unit p ON p.id = w.parent_id
            WHERE v.id IN (:ids)
            """, nativeQuery = true)
    List<SelectableVenueView> findSelectableByIdIn(@Param("ids") Collection<UUID> ids);

    /** Tăng version để thao tác ghi CAS của admin đang giữ dòng cũ phải thử lại thay vì ghi đè last_selected_at. */
    @Modifying
    @Query(value = "UPDATE venue SET last_selected_at = GREATEST(COALESCE(last_selected_at, :selectedAt), :selectedAt), "
            + "stale_alerted_at = NULL, version = version + 1 WHERE id IN (:ids)", nativeQuery = true)
    int markSelected(@Param("ids") Collection<UUID> ids, @Param("selectedAt") LocalDateTime selectedAt);

    /**
     * Claim một batch địa điểm ACTIVE không được chọn từ {@code threshold} và chưa cảnh báo. Khai báo không có
     * {@code @Modifying}: câu lệnh trả về dòng nên Spring Data chạy nó như một query. WHERE ngoài lặp lại mọi điều
     * kiện chọn (ACTIVE, chưa cảnh báo, quá ngưỡng): dòng bị sửa đồng thời chỉ được WHERE ngoài kiểm lại trên
     * phiên bản mới.
     */
    @Query(value = """
            UPDATE venue SET stale_alerted_at = :now, version = version + 1
            WHERE id IN (SELECT id FROM venue WHERE status = 'ACTIVE' AND stale_alerted_at IS NULL
                         AND COALESCE(last_selected_at, created_at) < :threshold ORDER BY id LIMIT :batchLimit)
              AND status = 'ACTIVE' AND stale_alerted_at IS NULL
              AND COALESCE(last_selected_at, created_at) < :threshold
            RETURNING id AS id, name AS name
            """, nativeQuery = true)
    List<StaleVenueView> markStaleAlerted(@Param("threshold") LocalDateTime threshold, @Param("now") LocalDateTime now,
                                          @Param("batchLimit") int batchLimit);

    /** Địa điểm, người tạo (kèm cờ đã xoá) và phường/tỉnh nếu có, trong một truy vấn. */
    @Query(value = """
            SELECT v.id AS venueId, v.name AS venueName, v.address AS address, v.latitude AS latitude,
                   v.longitude AS longitude, w.id AS wardId, w.name AS wardName,
                   p.id AS provinceId, p.name AS provinceName, v.venue_type AS venueType,
                   v.phone AS phone, v.website AS website, v.status AS status,
                   v.created_by_type AS createdByType, v.created_by_user_id AS createdById,
                   u.display_name AS createdByName, (u.deleted_at IS NOT NULL) AS createdByDeleted,
                   v.last_selected_at AS lastSelectedAt, v.admin_verified_inactive_at AS adminVerifiedInactiveAt,
                   v.created_at AS createdAt, v.updated_at AS updatedAt
            FROM venue v
            LEFT JOIN "user" u ON u.id = v.created_by_user_id
            LEFT JOIN administrative_unit w ON w.id = v.ward_id
            LEFT JOIN administrative_unit p ON p.id = w.parent_id
            WHERE v.id = :venueId
            """, nativeQuery = true)
    Optional<VenueDetailView> findDetailById(@Param("venueId") UUID venueId);

    @Query(value = """
            SELECT v.id AS venueId, v.name AS venueName, v.address AS address, v.latitude AS latitude,
                   v.longitude AS longitude, v.venue_type AS venueType, v.status AS status,
                   vi.image_url AS primaryImageUrl, NULL::double precision AS distanceKm,
                   v.last_selected_at AS lastSelectedAt
            FROM venue v
            LEFT JOIN venue_image vi ON vi.venue_id = v.id AND vi.is_primary
            WHERE v.status = :status
              AND (:qPattern IS NULL OR v.name ILIKE :qPattern OR v.address ILIKE :qPattern)
            ORDER BY v.name ASC, v.id ASC
            """, countQuery = """
            SELECT COUNT(*) FROM venue v
            WHERE v.status = :status
              AND (:qPattern IS NULL OR v.name ILIKE :qPattern OR v.address ILIKE :qPattern)
            """, nativeQuery = true)
    Page<VenueSummaryView> searchByStatus(@Param("status") String status, @Param("qPattern") String qPattern,
                                          Pageable pageable);

    @Query(value = """
            SELECT v.id AS venueId, v.name AS venueName, v.address AS address, v.latitude AS latitude,
                   v.longitude AS longitude, v.venue_type AS venueType, v.status AS status,
                   vi.image_url AS primaryImageUrl, NULL::double precision AS distanceKm,
                   v.last_selected_at AS lastSelectedAt
            FROM venue v
            LEFT JOIN venue_image vi ON vi.venue_id = v.id AND vi.is_primary
            WHERE (:qPattern IS NULL OR v.name ILIKE :qPattern OR v.address ILIKE :qPattern)
            ORDER BY v.name ASC, v.id ASC
            """, countQuery = """
            SELECT COUNT(*) FROM venue v
            WHERE (:qPattern IS NULL OR v.name ILIKE :qPattern OR v.address ILIKE :qPattern)
            """, nativeQuery = true)
    Page<VenueSummaryView> searchAll(@Param("qPattern") String qPattern, Pageable pageable);

    @Query(value = """
            SELECT * FROM (
                SELECT v.id AS venueId, v.name AS venueName, v.address AS address, v.latitude AS latitude,
                       v.longitude AS longitude, v.venue_type AS venueType, v.status AS status,
                       vi.image_url AS primaryImageUrl,
                       6371 * 2 * ASIN(SQRT(POWER(SIN(RADIANS(v.latitude - :lat) / 2), 2)
                           + COS(RADIANS(:lat)) * COS(RADIANS(v.latitude))
                           * POWER(SIN(RADIANS(v.longitude - :lng) / 2), 2))) AS distanceKm,
                       v.last_selected_at AS lastSelectedAt
                FROM venue v
                LEFT JOIN venue_image vi ON vi.venue_id = v.id AND vi.is_primary
                WHERE v.status = 'ACTIVE' AND v.latitude BETWEEN :minLat AND :maxLat
                  AND v.longitude BETWEEN :minLng AND :maxLng
                  AND (:qPattern IS NULL OR v.name ILIKE :qPattern OR v.address ILIKE :qPattern)
            ) nearby WHERE distanceKm <= :radiusKm ORDER BY distanceKm ASC, venueId ASC
            """, countQuery = """
            SELECT COUNT(*) FROM (
                SELECT 6371 * 2 * ASIN(SQRT(POWER(SIN(RADIANS(v.latitude - :lat) / 2), 2)
                    + COS(RADIANS(:lat)) * COS(RADIANS(v.latitude))
                    * POWER(SIN(RADIANS(v.longitude - :lng) / 2), 2))) AS distanceKm
                FROM venue v WHERE v.status = 'ACTIVE' AND v.latitude BETWEEN :minLat AND :maxLat
                    AND v.longitude BETWEEN :minLng AND :maxLng
                    AND (:qPattern IS NULL OR v.name ILIKE :qPattern OR v.address ILIKE :qPattern)
            ) nearby WHERE distanceKm <= :radiusKm
            """, nativeQuery = true)
    Page<VenueSummaryView> searchNearbyActive(@Param("qPattern") String qPattern, @Param("lat") double lat,
            @Param("lng") double lng, @Param("radiusKm") double radiusKm, @Param("minLat") double minLat,
            @Param("maxLat") double maxLat, @Param("minLng") double minLng, @Param("maxLng") double maxLng,
            Pageable pageable);
}
