package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.entity.venue.VenueImage;
import com.phananh.arenaflow.domain.projection.VenueImageStatsView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
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
}
