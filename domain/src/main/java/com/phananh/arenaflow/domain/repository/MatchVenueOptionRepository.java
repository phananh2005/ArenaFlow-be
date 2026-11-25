package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.entity.match.MatchVenueOption;
import com.phananh.arenaflow.domain.projection.MatchOptionRowView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface MatchVenueOptionRepository extends JpaRepository<MatchVenueOption, UUID> {

    /** Ghi mọi lựa chọn địa điểm của kèo trong một câu; thứ tự mảng = {@code display_order}; id do PostgreSQL sinh. */
    @Modifying
    @Query(value = """
            INSERT INTO match_venue_option (id, match_id, venue_id, display_order, created_at)
            SELECT uuidv7(), :matchId, t.venue_id, t.ord, :now
            FROM unnest(CAST(:venueIds AS uuid[])) WITH ORDINALITY AS t(venue_id, ord)
            """, nativeQuery = true)
    int insertAll(@Param("matchId") UUID matchId, @Param("venueIds") UUID[] venueIds, @Param("now") LocalDateTime now);

    /**
     * Lựa chọn địa điểm (kèm ảnh chính) và khung giờ của kèo trong một câu {@code UNION ALL}; {@code kind} phân biệt
     * loại dòng. Thứ tự: TIME theo giờ bắt đầu, VENUE theo thứ tự nhập.
     */
    @Query(value = """
            SELECT 'VENUE' AS kind, o.display_order AS displayOrder, v.id AS venueId, v.name AS venueName,
                   v.address AS address, v.latitude AS latitude, v.longitude AS longitude, v.status AS venueStatus,
                   img.image_url AS primaryImageUrl, CAST(NULL AS uuid) AS timeOptionId,
                   CAST(NULL AS timestamp) AS startsAt, CAST(NULL AS timestamp) AS endsAt
            FROM match_venue_option o
            JOIN venue v ON v.id = o.venue_id
            LEFT JOIN venue_image img ON img.venue_id = v.id AND img.is_primary
            WHERE o.match_id = :matchId
            UNION ALL
            SELECT 'TIME', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
                   t.id, t.proposed_start_time, t.proposed_end_time
            FROM match_time_option t
            WHERE t.match_id = :matchId
            ORDER BY kind, displayOrder, startsAt
            """, nativeQuery = true)
    List<MatchOptionRowView> findOptionRows(@Param("matchId") UUID matchId);
}
