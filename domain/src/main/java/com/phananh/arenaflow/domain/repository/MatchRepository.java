package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.entity.match.Match;
import com.phananh.arenaflow.domain.projection.MatchAuthorAccessView;
import com.phananh.arenaflow.domain.projection.MatchAvailabilityView;
import com.phananh.arenaflow.domain.projection.DueMatchView;
import com.phananh.arenaflow.domain.projection.MatchDetailView;
import com.phananh.arenaflow.domain.projection.MatchSummaryView;
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

/**
 * Kèo. Câu bulk {@code @Modifying} trên {@code match} luôn tăng {@code version} và gán {@code updated_at} để CAS của
 * thao tác người dùng đang giữ bản cũ phải thử lại. Trạng thái hiển thị tính trong SQL: kèo OPEN đã tới hạn → EXPIRED
 * (đọc không ghi). Tham số tuỳ chọn được CAST để PostgreSQL biết kiểu khi giá trị null.
 */
public interface MatchRepository extends JpaRepository<Match, UUID>, MatchRepositoryCustom {

    String EFFECTIVE_STATUS = "CASE WHEN m.status = 'OPEN' AND m.expires_at <= :now THEN 'EXPIRED' ELSE m.status END";

    /** Cột chung của kèo rút gọn (thiếu đội đã ghép, khoảng cách — mỗi câu tự thêm). */
    String SUMMARY_COLUMNS = """
            m.id AS matchId, m.type AS type, m.sport_id AS sportId, s.name AS sportName, m.format AS format,
            m.skill_levels AS skillLevels, m.match_start_time AS matchStartTime, m.match_end_time AS matchEndTime,
            m.expires_at AS expiresAt, m.author_team_id AS authorTeamId, t.name AS authorTeamName,
            t.reliability_score AS authorTeamReliabilityScore, m.confirmed_opponent_team_id AS opponentTeamId,
            v.id AS venueId, v.name AS venueName, v.address AS venueAddress,
            CAST(NULL AS integer) AS venueOptionCount, CAST(NULL AS integer) AS timeOptionCount, m.created_at AS createdAt
            """;

    /** Kèo đang tìm đối: OPEN chưa quá hạn, đội tạo kèo ACTIVE, bộ môn đang bật + bộ lọc tuỳ chọn. */
    String OPEN_SEARCH_FILTER = """
            m.status = 'OPEN' AND m.expires_at > :now
              AND (CAST(:type AS varchar) IS NULL OR m.type = CAST(:type AS varchar))
              AND (CAST(:sportId AS uuid) IS NULL OR m.sport_id = CAST(:sportId AS uuid))
              AND (CAST(:format AS varchar) IS NULL OR m.format = CAST(:format AS varchar))
              AND (CAST(:skillLevel AS varchar) IS NULL OR m.skill_levels = '[]'
                   OR jsonb_exists(CAST(m.skill_levels AS jsonb), CAST(:skillLevel AS varchar)))
              AND (CAST(:startsAtFrom AS timestamp) IS NULL OR m.match_start_time >= CAST(:startsAtFrom AS timestamp))
              AND (CAST(:startsAtTo AS timestamp) IS NULL OR m.match_start_time <= CAST(:startsAtTo AS timestamp))
            """;

    String OPEN_SEARCH_JOINS = """
            FROM match m
            JOIN team t ON t.id = m.author_team_id AND t.status = 'ACTIVE'
            JOIN sport s ON s.id = m.sport_id AND s.is_active
            """;

    String DISTANCE = """
            6371 * 2 * ASIN(SQRT(POWER(SIN(RADIANS(v.latitude - :lat) / 2), 2)
                + COS(RADIANS(:lat)) * COS(RADIANS(v.latitude)) * POWER(SIN(RADIANS(v.longitude - :lng) / 2), 2)))
            """;

    String NEARBY_FILTER = """
            v.latitude BETWEEN :minLat AND :maxLat AND v.longitude BETWEEN :minLng AND :maxLng
            """;

    String TEAM_MATCH_FILTER = """
            ((CAST(:role AS varchar) IS NULL AND (m.author_team_id = :teamId OR m.confirmed_opponent_team_id = :teamId))
              OR (CAST(:role AS varchar) = 'AUTHOR' AND m.author_team_id = :teamId)
              OR (CAST(:role AS varchar) = 'OPPONENT' AND m.confirmed_opponent_team_id = :teamId))
              AND (CAST(:status AS varchar) IS NULL OR
            """ + EFFECTIVE_STATUS + " = CAST(:status AS varchar))";

    /** Chi tiết kèo một câu; địa điểm LEFT JOIN (kèo luồng B chưa ghép không có địa điểm). */
    @Query(value = "SELECT m.id AS matchId, m.type AS type, " + EFFECTIVE_STATUS + " AS effectiveStatus, " + """
            m.sport_id AS sportId, s.name AS sportName, m.format AS format, m.skill_levels AS skillLevels,
            m.age_note AS ageNote, m.match_nature AS matchNature, m.match_start_time AS matchStartTime,
            m.match_end_time AS matchEndTime, m.expires_at AS expiresAt,
            m.author_team_id AS authorTeamId, ta.name AS authorTeamName, ta.avatar_url AS authorTeamAvatarUrl,
            ta.reliability_score AS authorTeamReliabilityScore,
            m.confirmed_opponent_team_id AS opponentTeamId, tb.name AS opponentTeamName,
            tb.avatar_url AS opponentTeamAvatarUrl, tb.reliability_score AS opponentTeamReliabilityScore,
            v.id AS venueId, v.name AS venueName, v.address AS venueAddress, v.latitude AS venueLatitude,
            v.longitude AS venueLongitude, v.status AS venueStatus, vi.image_url AS venuePrimaryImageUrl,
            m.cost_type AS costType, m.cost_split AS costSplit, m.cost_venue_amount AS costVenueAmount,
            m.cost_drinks_amount AS costDrinksAmount, CAST(NULL AS bigint) AS costDepositAmount,
            m.cost_deposit_holder AS costDepositHolder, CAST(NULL AS uuid) AS confirmedPairingId,
            m.cancelled_by_team_id AS cancelledByTeamId, m.cancelled_at AS cancelledAt, m.cancel_reason AS cancelReason,
            m.created_at AS createdAt, m.updated_at AS updatedAt
            FROM match m
            JOIN sport s ON s.id = m.sport_id
            JOIN team ta ON ta.id = m.author_team_id
            LEFT JOIN team tb ON tb.id = m.confirmed_opponent_team_id
            LEFT JOIN venue v ON v.id = m.venue_id
            LEFT JOIN venue_image vi ON vi.venue_id = v.id AND vi.is_primary
            WHERE m.id = :id
            """, nativeQuery = true)
    Optional<MatchDetailView> findDetailById(@Param("id") UUID id, @Param("now") LocalDateTime now);

    /** Tìm kèo không theo toạ độ: {@code startsAt} ↑, id ↑. */
    @Query(value = "SELECT " + SUMMARY_COLUMNS + """
            , m.status AS effectiveStatus, CAST(NULL AS varchar) AS opponentTeamName,
            CAST(NULL AS double precision) AS distanceKm
            """ + OPEN_SEARCH_JOINS + "LEFT JOIN venue v ON v.id = m.venue_id WHERE " + OPEN_SEARCH_FILTER
            + " ORDER BY m.match_start_time ASC, m.id ASC",
            countQuery = "SELECT COUNT(*) " + OPEN_SEARCH_JOINS + "WHERE " + OPEN_SEARCH_FILTER,
            nativeQuery = true)
    Page<MatchSummaryView> searchOpen(@Param("type") String type, @Param("sportId") UUID sportId,
                                      @Param("format") String format, @Param("skillLevel") String skillLevel,
                                      @Param("startsAtFrom") LocalDateTime startsAtFrom,
                                      @Param("startsAtTo") LocalDateTime startsAtTo,
                                      @Param("now") LocalDateTime now, Pageable pageable);

    /**
     * Tìm kèo theo toạ độ: hộp giới hạn lọc trước, Haversine lọc bán kính; khoảng cách ↑, {@code startsAt} ↑, id ↑.
     */
    @Query(value = "SELECT * FROM (SELECT " + SUMMARY_COLUMNS + """
            , m.status AS effectiveStatus, CAST(NULL AS varchar) AS opponentTeamName,
            """ + DISTANCE + " AS distanceKm " + OPEN_SEARCH_JOINS + "JOIN venue v ON v.id = m.venue_id WHERE "
            + OPEN_SEARCH_FILTER + " AND " + NEARBY_FILTER + """
            ) nearby WHERE distanceKm <= :radiusKm ORDER BY distanceKm ASC, matchStartTime ASC, matchId ASC
            """,
            countQuery = "SELECT COUNT(*) FROM (SELECT " + DISTANCE + " AS distanceKm " + OPEN_SEARCH_JOINS
                    + "JOIN venue v ON v.id = m.venue_id WHERE " + OPEN_SEARCH_FILTER + " AND " + NEARBY_FILTER
                    + ") nearby WHERE distanceKm <= :radiusKm",
            nativeQuery = true)
    Page<MatchSummaryView> searchOpenNearby(@Param("type") String type, @Param("sportId") UUID sportId,
                                            @Param("format") String format, @Param("skillLevel") String skillLevel,
                                            @Param("startsAtFrom") LocalDateTime startsAtFrom,
                                            @Param("startsAtTo") LocalDateTime startsAtTo,
                                            @Param("now") LocalDateTime now, @Param("lat") double lat,
                                            @Param("lng") double lng, @Param("radiusKm") double radiusKm,
                                            @Param("minLat") double minLat, @Param("maxLat") double maxLat,
                                            @Param("minLng") double minLng, @Param("maxLng") double maxLng,
                                            Pageable pageable);

    /**
     * Kèo của một đội ở mọi trạng thái; {@code role} null = tạo hoặc đã ghép; {@code status} lọc trên trạng thái
     * hiển thị. {@code startsAt} ↓, id ↓.
     */
    @Query(value = "SELECT " + SUMMARY_COLUMNS + ", " + EFFECTIVE_STATUS + " AS effectiveStatus, " + """
            tb.name AS opponentTeamName, CAST(NULL AS double precision) AS distanceKm
            FROM match m
            JOIN sport s ON s.id = m.sport_id
            JOIN team t ON t.id = m.author_team_id
            LEFT JOIN team tb ON tb.id = m.confirmed_opponent_team_id
            LEFT JOIN venue v ON v.id = m.venue_id
            WHERE """ + " " + TEAM_MATCH_FILTER + " ORDER BY m.match_start_time DESC, m.id DESC",
            countQuery = "SELECT COUNT(*) FROM match m WHERE " + TEAM_MATCH_FILTER,
            nativeQuery = true)
    Page<MatchSummaryView> findTeamMatches(@Param("teamId") UUID teamId, @Param("role") String role,
                                           @Param("status") String status, @Param("now") LocalDateTime now,
                                           Pageable pageable);

    /** Quyền xem yêu cầu của kèo: đội tạo kèo, trạng thái đội, người gọi có là đội trưởng ACTIVE — một câu. */
    @Query(value = """
            SELECT m.author_team_id AS authorTeamId, t.status AS authorTeamStatus, m.type AS type,
                   EXISTS (SELECT 1 FROM team_member tm WHERE tm.team_id = m.author_team_id AND tm.user_id = :userId
                           AND tm.role = 'CAPTAIN' AND tm.status = 'ACTIVE') AS callerIsCaptain
            FROM match m
            JOIN team t ON t.id = m.author_team_id
            WHERE m.id = :matchId
            """, nativeQuery = true)
    Optional<MatchAuthorAccessView> findAuthorAccess(@Param("matchId") UUID matchId, @Param("userId") UUID userId);

    /**
     * Trạng thái đội tạo kèo + bộ môn đang bật + đội trưởng hiện tại của đội tạo kèo + cuộc trò chuyện đã có của cặp
     * (kèo, {@code teamId}) — một câu (index unique của cuộc trò chuyện theo kèo + đội tham gia).
     */
    @Query(value = """
            SELECT ta.status AS authorTeamStatus, s.is_active AS sportActive, ta.name AS authorTeamName,
                   ta.avatar_url AS authorTeamAvatarUrl, ta.reliability_score AS authorTeamReliabilityScore,
                   cu.display_name AS authorCaptainName, (cu.deleted_at IS NOT NULL) AS authorCaptainDeleted,
                   cv.id AS existingConversationId
            FROM match m
            JOIN team ta ON ta.id = m.author_team_id
            JOIN sport s ON s.id = m.sport_id
            LEFT JOIN team_member cm ON cm.team_id = m.author_team_id AND cm.role = 'CAPTAIN' AND cm.status = 'ACTIVE'
            LEFT JOIN "user" cu ON cu.id = cm.user_id
            LEFT JOIN conversation cv ON cv.context_type = 'TEAM_MATCH' AND cv.match_id = m.id AND cv.party_b_id = :teamId
            WHERE m.id = :matchId
            """, nativeQuery = true)
    MatchAvailabilityView findAvailability(@Param("matchId") UUID matchId, @Param("teamId") UUID teamId);

    /**
     * Job: claim một lô kèo OPEN đã quá hạn, khoá dòng {@code FOR NO KEY UPDATE SKIP LOCKED} — dòng đang bị thao tác
     * khác giữ được bỏ qua (lần sau xử lý); nhiều instance không xử lý trùng. Câu đọc nên không {@code @Modifying}.
     */
    @Query(value = """
            SELECT m.id AS id, m.author_team_id AS authorTeamId, m.confirmed_opponent_team_id AS opponentTeamId,
                   m.match_end_time AS matchEndTime
            FROM match m
            WHERE m.status = 'OPEN' AND m.expires_at <= :now
            ORDER BY m.expires_at, m.id
            LIMIT :limit
            FOR NO KEY UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<DueMatchView> claimDueForExpiry(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /** Job: claim một lô kèo CONFIRMED đã qua giờ kết thúc, khoá như {@link #claimDueForExpiry}. */
    @Query(value = """
            SELECT m.id AS id, m.author_team_id AS authorTeamId, m.confirmed_opponent_team_id AS opponentTeamId,
                   m.match_end_time AS matchEndTime
            FROM match m
            WHERE m.status = 'CONFIRMED' AND m.match_end_time <= :now
            ORDER BY m.match_end_time, m.id
            LIMIT :limit
            FOR NO KEY UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<DueMatchView> claimDueForArchive(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /** Lặp điều kiện trạng thái nguồn làm cổng chống chuyển hai lần. */
    @Modifying
    @Query(value = """
            UPDATE match SET status = 'EXPIRED', version = version + 1, updated_at = :now
            WHERE id IN (:ids) AND status = 'OPEN'
            """, nativeQuery = true)
    int markExpired(@Param("ids") Collection<UUID> ids, @Param("now") LocalDateTime now);

    @Modifying
    @Query(value = """
            UPDATE match SET status = 'ARCHIVED', version = version + 1, updated_at = :now
            WHERE id IN (:ids) AND status = 'CONFIRMED'
            """, nativeQuery = true)
    int markArchived(@Param("ids") Collection<UUID> ids, @Param("now") LocalDateTime now);

    /**
     * Chuyển kèo OPEN đã quá hạn sang EXPIRED; 0 dòng = kèo đã được job/request khác chuyển hoặc chưa quá hạn.
     */
    @Modifying
    @Query(value = """
            UPDATE match SET status = 'EXPIRED', version = version + 1, updated_at = :now
            WHERE id = :id AND status = 'OPEN' AND expires_at <= :now
            """, nativeQuery = true)
    int expireIfDue(@Param("id") UUID id, @Param("now") LocalDateTime now);
}
