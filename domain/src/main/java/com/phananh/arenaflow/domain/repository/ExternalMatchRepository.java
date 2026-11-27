package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.entity.mercenary.ExternalMatch;
import com.phananh.arenaflow.domain.projection.ExternalMatchContextView;
import com.phananh.arenaflow.domain.projection.ExternalMatchView;
import com.phananh.arenaflow.domain.projection.RecruitContextView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Trận ngoài hệ thống. Sửa / huỷ là câu UPDATE có điều kiện {@code version} / trạng thái và tự tăng {@code version} —
 * khoá dòng trận ngoài trước khi khoá tin của trận.
 */
public interface ExternalMatchRepository extends JpaRepository<ExternalMatch, UUID> {

    String VIEW_SELECT = """
            SELECT x.id AS externalMatchId, x.team_id AS teamId, t.status AS teamStatus,
                   EXISTS (SELECT 1 FROM team_member cm WHERE cm.team_id = x.team_id AND cm.user_id = :callerId
                           AND cm.role = 'CAPTAIN' AND cm.status = 'ACTIVE') AS callerCaptain,
                   x.status AS status, x.match_start_time AS startsAt, x.match_end_time AS endsAt,
                   x.skill_levels AS skillLevels, x.opponent_name AS opponentName,
                   v.id AS venueId, v.name AS venueName, v.address AS venueAddress, v.latitude AS venueLatitude,
                   v.longitude AS venueLongitude, vw.name AS wardName, vp.name AS provinceName,
                   EXISTS (SELECT 1 FROM mercenary_recruitment r WHERE r.external_match_id = x.id
                           AND r.quantity_approved > 0) AS hasApprovedMercenary,
                   x.cancelled_at AS cancelledAt, x.version AS version, x.created_at AS createdAt,
                   x.updated_at AS updatedAt
            FROM external_match x
            JOIN team t ON t.id = x.team_id
            JOIN venue v ON v.id = x.venue_id
            LEFT JOIN administrative_unit vw ON vw.id = v.ward_id
            LEFT JOIN administrative_unit vp ON vp.id = vw.parent_id
            """;

    /** Context sửa / huỷ: trận + sân + khu vực + có mercenary được duyệt + trạng thái đội + cờ đội trưởng. */
    @Query(value = VIEW_SELECT + " WHERE x.id = :id", nativeQuery = true)
    Optional<ExternalMatchContextView> findContext(@Param("id") UUID id, @Param("callerId") UUID callerId);

    /** Chi tiết (cùng cột context). */
    @Query(value = VIEW_SELECT + " WHERE x.id = :id", nativeQuery = true)
    Optional<ExternalMatchView> findDetail(@Param("id") UUID id, @Param("callerId") UUID callerId);

    /**
     * Trận ngoài của đội: {@code UPCOMING} (SCHEDULED chưa tới giờ kết thúc, giờ bắt đầu tăng dần), {@code ENDED},
     * {@code CANCELLED}, null = tất cả (giờ bắt đầu giảm dần).
     */
    @Query(value = VIEW_SELECT + """
             WHERE x.team_id = :teamId
               AND (CAST(:status AS varchar) IS NULL
                    OR (CAST(:status AS varchar) = 'UPCOMING' AND x.status = 'SCHEDULED' AND x.match_end_time > :now)
                    OR (CAST(:status AS varchar) = 'ENDED' AND x.status = 'SCHEDULED' AND x.match_end_time <= :now)
                    OR (CAST(:status AS varchar) = 'CANCELLED' AND x.status = 'CANCELLED'))
            ORDER BY CASE WHEN CAST(:status AS varchar) = 'UPCOMING' THEN x.match_start_time END ASC,
                     CASE WHEN CAST(:status AS varchar) = 'UPCOMING' THEN x.id END ASC,
                     x.match_start_time DESC, x.id DESC
            """, countQuery = """
            SELECT COUNT(*) FROM external_match x
            WHERE x.team_id = :teamId AND CAST(:callerId AS uuid) IS NOT NULL
              AND (CAST(:status AS varchar) IS NULL
                   OR (CAST(:status AS varchar) = 'UPCOMING' AND x.status = 'SCHEDULED' AND x.match_end_time > :now)
                   OR (CAST(:status AS varchar) = 'ENDED' AND x.status = 'SCHEDULED' AND x.match_end_time <= :now)
                   OR (CAST(:status AS varchar) = 'CANCELLED' AND x.status = 'CANCELLED'))
            """, nativeQuery = true)
    Page<ExternalMatchView> findByTeam(@Param("teamId") UUID teamId, @Param("callerId") UUID callerId,
                                       @Param("status") String status, @Param("now") LocalDateTime now,
                                       Pageable pageable);

    /** Context thêm vị trí cho trận ngoài: cùng cột context tạo tin của kèo, đội = đội khai báo. */
    @Query(value = """
            SELECT 'EXTERNAL' AS source, CAST(NULL AS uuid) AS matchId, x.id AS externalMatchId, x.status AS matchStatus,
                   x.match_start_time AS gameStart, x.match_end_time AS gameEnd, x.team_id AS authorTeamId,
                   CAST(NULL AS uuid) AS opponentTeamId, CAST(NULL AS varchar) AS format, x.skill_levels AS skillLevels,
                   x.opponent_name AS opponentName,
                   t.id AS teamId, t.status AS teamStatus, t.name AS teamName, t.avatar_url AS teamAvatarUrl,
                   EXISTS (SELECT 1 FROM team_member cm WHERE cm.team_id = t.id AND cm.user_id = :callerId
                           AND cm.role = 'CAPTAIN' AND cm.status = 'ACTIVE') AS callerCaptain,
                   me.display_name AS callerName, (me.deleted_at IS NOT NULL) AS callerDeleted,
                   v.id AS venueId, v.name AS venueName, v.address AS venueAddress, v.latitude AS venueLatitude,
                   v.longitude AS venueLongitude, vw.name AS wardName, vp.name AS provinceName,
                   ARRAY_TO_STRING(ARRAY(SELECT r.position FROM mercenary_recruitment r
                                         WHERE r.external_match_id = x.id
                                           AND r.status IN ('OPEN', 'FILLED')), '|') AS activePositions
            FROM external_match x
            JOIN team t ON t.id = x.team_id
            LEFT JOIN "user" me ON me.id = :callerId
            LEFT JOIN venue v ON v.id = x.venue_id
            LEFT JOIN administrative_unit vw ON vw.id = v.ward_id
            LEFT JOIN administrative_unit vp ON vp.id = vw.parent_id
            WHERE x.id = :id
            """, nativeQuery = true)
    Optional<RecruitContextView> findRecruitContext(@Param("id") UUID id, @Param("callerId") UUID callerId);

    /**
     * Sửa trận ngoài (CAS {@code version}, còn SCHEDULED và chưa tới giờ kết thúc); tự tăng {@code version}. Khoá dòng
     * trận ngoài trước khi khoá tin.
     */
    @Modifying
    @Query(value = """
            UPDATE external_match
            SET venue_id = :venueId, match_start_time = :start, match_end_time = :end, skill_levels = :skillLevels,
                opponent_name = :opponentName, version = version + 1, updated_at = :now
            WHERE id = :id AND version = :expectedVersion AND status = 'SCHEDULED' AND match_end_time > :now
            """, nativeQuery = true)
    int updateIfVersion(@Param("id") UUID id, @Param("expectedVersion") long expectedVersion,
                        @Param("venueId") UUID venueId, @Param("start") LocalDateTime start,
                        @Param("end") LocalDateTime end, @Param("skillLevels") String skillLevels,
                        @Param("opponentName") String opponentName, @Param("now") LocalDateTime now);

    /** Huỷ trận ngoài chỉ trước giờ bắt đầu; tự tăng {@code version}. 0 dòng = trận vừa đổi / đã bắt đầu. */
    @Modifying
    @Query(value = """
            UPDATE external_match
            SET status = 'CANCELLED', cancelled_at = :now, version = version + 1, updated_at = :now
            WHERE id = :id AND status = 'SCHEDULED' AND match_start_time > :now
            """, nativeQuery = true)
    int cancelIfScheduled(@Param("id") UUID id, @Param("now") LocalDateTime now);
}
