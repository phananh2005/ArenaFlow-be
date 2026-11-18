package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.entity.venue.Venue;
import com.phananh.arenaflow.domain.projection.VenueDetailView;
import com.phananh.arenaflow.domain.projection.VenueSummaryView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface VenueRepository extends JpaRepository<Venue, UUID> {

    /** Acquires PostgreSQL row lock before image writes and bumps version for concurrent CAS writers. */
    @Modifying
    @Query(value = "UPDATE venue SET version = version + 1 WHERE id = :venueId", nativeQuery = true)
    int lockRow(@Param("venueId") UUID venueId);

    /** Venue, creator (including deleted flag) and optional ward/province in a single query. */
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
                WHERE (:status IS NULL OR v.status = :status)
                  AND v.latitude BETWEEN :minLat AND :maxLat AND v.longitude BETWEEN :minLng AND :maxLng
                  AND (:qPattern IS NULL OR v.name ILIKE :qPattern OR v.address ILIKE :qPattern)
            ) nearby WHERE distanceKm <= :radiusKm ORDER BY distanceKm ASC, venueId ASC
            """, countQuery = """
            SELECT COUNT(*) FROM (
                SELECT 6371 * 2 * ASIN(SQRT(POWER(SIN(RADIANS(v.latitude - :lat) / 2), 2)
                    + COS(RADIANS(:lat)) * COS(RADIANS(v.latitude))
                    * POWER(SIN(RADIANS(v.longitude - :lng) / 2), 2))) AS distanceKm
                FROM venue v WHERE (:status IS NULL OR v.status = :status)
                    AND v.latitude BETWEEN :minLat AND :maxLat AND v.longitude BETWEEN :minLng AND :maxLng
                    AND (:qPattern IS NULL OR v.name ILIKE :qPattern OR v.address ILIKE :qPattern)
            ) nearby WHERE distanceKm <= :radiusKm
            """, nativeQuery = true)
    Page<VenueSummaryView> searchNearbyAny(@Param("status") String status, @Param("qPattern") String qPattern,
            @Param("lat") double lat, @Param("lng") double lng, @Param("radiusKm") double radiusKm,
            @Param("minLat") double minLat, @Param("maxLat") double maxLat, @Param("minLng") double minLng,
            @Param("maxLng") double maxLng, Pageable pageable);
}
