package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.entity.venue.VenueImage;
import com.phananh.arenaflow.domain.projection.VenueImageStatsView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VenueImageRepository extends JpaRepository<VenueImage, UUID> {

    List<VenueImage> findByVenueIdOrderByDisplayOrderAsc(UUID venueId);

    long countByVenueId(UUID venueId);

    @Query(value = "SELECT COUNT(*) AS imageCount, COALESCE(MAX(display_order), 0) AS maxDisplayOrder "
            + "FROM venue_image WHERE venue_id = :venueId", nativeQuery = true)
    VenueImageStatsView findStatsByVenueId(@Param("venueId") UUID venueId);

    @Modifying
    @Query("UPDATE VenueImage image SET image.isPrimary = false WHERE image.venueId = :venueId AND image.isPrimary = true")
    int clearPrimary(@Param("venueId") UUID venueId);

    @Modifying
    @Query("UPDATE VenueImage image SET image.isPrimary = true WHERE image.id = :imageId AND image.venueId = :venueId")
    int setPrimary(@Param("venueId") UUID venueId, @Param("imageId") UUID imageId);

    Optional<VenueImage> findByIdAndVenueId(UUID id, UUID venueId);

    // Native update: Hibernate flush lệnh DELETE ảnh đang chờ trước khi chạy câu này.
    @Modifying
    @Query(value = "UPDATE venue_image SET is_primary = TRUE WHERE id = (SELECT id FROM venue_image "
            + "WHERE venue_id = :venueId ORDER BY display_order, id LIMIT 1)", nativeQuery = true)
    int promoteFirstAsPrimary(@Param("venueId") UUID venueId);

    /** Job dọn dẹp: những public id nào trong số này (tối đa một trang) còn được tham chiếu. */
    @Query("SELECT image.publicId FROM VenueImage image WHERE image.publicId IN :publicIds")
    List<String> findExistingPublicIds(@Param("publicIds") Collection<String> publicIds);
}
