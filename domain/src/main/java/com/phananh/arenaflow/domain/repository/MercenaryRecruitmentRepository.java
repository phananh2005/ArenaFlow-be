package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.entity.mercenary.MercenaryRecruitment;
import com.phananh.arenaflow.domain.projection.ApprovedCountView;
import com.phananh.arenaflow.domain.projection.DueRecruitmentView;
import com.phananh.arenaflow.domain.projection.RecruitContextView;
import com.phananh.arenaflow.domain.projection.RecruitmentRowView;
import com.phananh.arenaflow.domain.projection.RecruitmentView;
import com.phananh.arenaflow.domain.projection.RecruitmentWriteContextView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tin tuyển mercenary. Đổi trạng thái / số lượng chỉ bằng câu UPDATE có điều kiện (0 dòng = dữ liệu vừa đổi → caller
 * ném {@code ConcurrencyConflictException}). Câu {@code … RETURNING} khai native trả projection, không {@code @Modifying}.
 */
public interface MercenaryRecruitmentRepository extends JpaRepository<MercenaryRecruitment, UUID> {

    /**
     * Context tạo tin cho kèo: trận + đội {@code teamId} (null khi không có) + cờ người gọi là đội trưởng ACTIVE + sân +
     * khu vực + vị trí đã có tin OPEN/FILLED của đội cho trận (nối bằng {@code |}). Rỗng = không có kèo.
     */
    @Query(value = """
            SELECT 'SYSTEM' AS source, m.id AS matchId, CAST(NULL AS uuid) AS externalMatchId, m.status AS matchStatus,
                   m.match_start_time AS gameStart, m.match_end_time AS gameEnd, m.author_team_id AS authorTeamId,
                   m.confirmed_opponent_team_id AS opponentTeamId, m.format AS format, m.skill_levels AS skillLevels,
                   CAST(NULL AS varchar) AS opponentName,
                   t.id AS teamId, t.status AS teamStatus, t.name AS teamName, t.avatar_url AS teamAvatarUrl,
                   EXISTS (SELECT 1 FROM team_member cm WHERE cm.team_id = t.id AND cm.user_id = :callerId
                           AND cm.role = 'CAPTAIN' AND cm.status = 'ACTIVE') AS callerCaptain,
                   me.display_name AS callerName, (me.deleted_at IS NOT NULL) AS callerDeleted,
                   v.id AS venueId, v.name AS venueName, v.address AS venueAddress, v.latitude AS venueLatitude,
                   v.longitude AS venueLongitude, vw.name AS wardName, vp.name AS provinceName,
                   ARRAY_TO_STRING(ARRAY(SELECT r.position FROM mercenary_recruitment r
                                         WHERE r.match_id = m.id AND r.team_id = :teamId
                                           AND r.status IN ('OPEN', 'FILLED')), '|') AS activePositions
            FROM match m
            LEFT JOIN team t ON t.id = :teamId
            LEFT JOIN "user" me ON me.id = :callerId
            LEFT JOIN venue v ON v.id = m.venue_id
            LEFT JOIN administrative_unit vw ON vw.id = v.ward_id
            LEFT JOIN administrative_unit vp ON vp.id = vw.parent_id
            WHERE m.id = :matchId
            """, nativeQuery = true)
    Optional<RecruitContextView> findRecruitContext(@Param("matchId") UUID matchId, @Param("teamId") UUID teamId,
                                                    @Param("callerId") UUID callerId);

    /**
     * Tạo mọi vị trí trong một câu ({@code unnest} hai mảng song song), id UUID v7 sinh ở DB; vi phạm index vị trí đang
     * mở → {@code DataIntegrityViolationException} ngay tại câu này. Trả các dòng vừa tạo (thứ tự không đảm bảo).
     */
    @Query(value = """
            INSERT INTO mercenary_recruitment (id, match_id, external_match_id, team_id, position, quantity_needed,
                                               quantity_approved, status, created_by_user_id, created_at, updated_at)
            SELECT uuidv7(), CAST(:matchId AS uuid), CAST(:externalMatchId AS uuid), :teamId, p.pos, p.qty, 0, 'OPEN',
                   :createdBy, :now, :now
            FROM unnest(CAST(:positions AS text[]), CAST(:quantities AS int[])) AS p(pos, qty)
            RETURNING id AS id, position AS position, quantity_needed AS quantityNeeded, created_at AS createdAt
            """, nativeQuery = true)
    List<RecruitmentRowView> insertAll(@Param("matchId") UUID matchId, @Param("externalMatchId") UUID externalMatchId,
                                       @Param("teamId") UUID teamId, @Param("createdBy") UUID createdBy,
                                       @Param("positions") String[] positions, @Param("quantities") int[] quantities,
                                       @Param("now") LocalDateTime now);

    /**
     * Tin của đội (cả kèo và trận ngoài), lọc trên trạng thái hiệu lực: {@code ACTIVE} = OPEN + FILLED, null = tất cả.
     * Sắp xếp: giờ bắt đầu trận, rồi thời điểm tạo, rồi id.
     */
    @Query(value = "SELECT * FROM (SELECT " + MercenarySql.RECRUITMENT_COLUMNS
            + ", CAST(NULL AS double precision) AS distanceKm" + MercenarySql.RECRUITMENT_FROM + """
             WHERE r.team_id = :teamId AND (CAST(:matchId AS uuid) IS NULL OR r.match_id = CAST(:matchId AS uuid)
                                             OR r.external_match_id = CAST(:matchId AS uuid))
            ) q
            WHERE CAST(:status AS varchar) IS NULL
               OR (CAST(:status AS varchar) = 'ACTIVE' AND q.effectiveStatus IN ('OPEN', 'FILLED'))
               OR q.effectiveStatus = CAST(:status AS varchar)
            ORDER BY q.gameStart ASC, q.createdAt ASC, q.recruitmentId ASC
            """, countQuery = "SELECT COUNT(*) FROM (SELECT " + MercenarySql.RECRUITMENT_EFFECTIVE_STATUS
            + " AS effectiveStatus FROM mercenary_recruitment r " + MercenarySql.GAME_JOIN + """
             WHERE r.team_id = :teamId AND (CAST(:matchId AS uuid) IS NULL OR r.match_id = CAST(:matchId AS uuid)
                                             OR r.external_match_id = CAST(:matchId AS uuid))
               AND CAST(:callerId AS uuid) IS NOT NULL
            ) q
            WHERE CAST(:status AS varchar) IS NULL
               OR (CAST(:status AS varchar) = 'ACTIVE' AND q.effectiveStatus IN ('OPEN', 'FILLED'))
               OR q.effectiveStatus = CAST(:status AS varchar)
            """, nativeQuery = true)
    Page<RecruitmentView> findByTeam(@Param("teamId") UUID teamId, @Param("callerId") UUID callerId,
                                     @Param("matchId") UUID matchId, @Param("status") String status,
                                     @Param("now") LocalDateTime now, Pageable pageable);

    /**
     * Tìm tin OPEN của trận đã xác nhận, chưa bắt đầu, đội tuyển ACTIVE, sân trong bán kính (lọc hộp toạ độ trước rồi
     * Haversine). Sắp xếp: khoảng cách, giờ bắt đầu, id. Toạ độ chỉ dùng trong câu, không lưu.
     */
    @Query(value = "SELECT * FROM (SELECT " + MercenarySql.RECRUITMENT_COLUMNS + ", " + MercenarySql.DISTANCE_KM
            + " AS distanceKm" + MercenarySql.RECRUITMENT_FROM + " WHERE r.status = 'OPEN' AND "
            + MercenarySql.GAME_CONFIRMED + " AND " + MercenarySql.GAME_START + " > :now" + """
               AND t.status = 'ACTIVE'
               AND (CAST(:position AS varchar) IS NULL OR r.position = CAST(:position AS varchar))
               AND v.latitude BETWEEN :minLat AND :maxLat AND v.longitude BETWEEN :minLng AND :maxLng
            ) q WHERE q.distanceKm <= :radiusKm
            ORDER BY q.distanceKm ASC, q.gameStart ASC, q.recruitmentId ASC
            """, countQuery = "SELECT COUNT(*) FROM (SELECT " + MercenarySql.DISTANCE_KM + " AS distanceKm"
            + " FROM mercenary_recruitment r " + MercenarySql.GAME_JOIN + """
             JOIN team t ON t.id = r.team_id
             JOIN venue v ON v.id = COALESCE(m.venue_id, x.venue_id)
             WHERE r.status = 'OPEN' AND COALESCE(m.status, x.status) IN ('CONFIRMED', 'SCHEDULED')
               AND COALESCE(m.match_start_time, x.match_start_time) > :now AND t.status = 'ACTIVE'
               AND (CAST(:position AS varchar) IS NULL OR r.position = CAST(:position AS varchar))
               AND v.latitude BETWEEN :minLat AND :maxLat AND v.longitude BETWEEN :minLng AND :maxLng
               AND CAST(:callerId AS uuid) IS NOT NULL
            ) q WHERE q.distanceKm <= :radiusKm
            """, nativeQuery = true)
    Page<RecruitmentView> searchNearby(@Param("callerId") UUID callerId, @Param("position") String position,
                                       @Param("now") LocalDateTime now, @Param("lat") double lat,
                                       @Param("lng") double lng, @Param("radiusKm") double radiusKm,
                                       @Param("minLat") double minLat, @Param("maxLat") double maxLat,
                                       @Param("minLng") double minLng, @Param("maxLng") double maxLng,
                                       Pageable pageable);

    /**
     * Như {@link #searchNearby} nhưng lọc sân cùng tỉnh với hồ sơ; sân cùng phường lên đầu, rồi giờ bắt đầu, id. Sân
     * chưa có phường không xuất hiện.
     */
    @Query(value = "SELECT " + MercenarySql.RECRUITMENT_COLUMNS + ", CAST(NULL AS double precision) AS distanceKm"
            + MercenarySql.RECRUITMENT_FROM + " WHERE r.status = 'OPEN' AND " + MercenarySql.GAME_CONFIRMED
            + " AND " + MercenarySql.GAME_START + " > :now" + """
               AND t.status = 'ACTIVE'
               AND (CAST(:position AS varchar) IS NULL OR r.position = CAST(:position AS varchar))
               AND vw.parent_id = :provinceId
            ORDER BY (v.ward_id = :wardId) DESC, COALESCE(m.match_start_time, x.match_start_time) ASC, r.id ASC
            """, countQuery = "SELECT COUNT(*) FROM mercenary_recruitment r " + MercenarySql.GAME_JOIN + """
             JOIN team t ON t.id = r.team_id
             JOIN venue v ON v.id = COALESCE(m.venue_id, x.venue_id)
             JOIN administrative_unit vw ON vw.id = v.ward_id
             WHERE r.status = 'OPEN' AND COALESCE(m.status, x.status) IN ('CONFIRMED', 'SCHEDULED')
               AND COALESCE(m.match_start_time, x.match_start_time) > :now AND t.status = 'ACTIVE'
               AND (CAST(:position AS varchar) IS NULL OR r.position = CAST(:position AS varchar))
               AND vw.parent_id = :provinceId
               AND CAST(:callerId AS uuid) IS NOT NULL AND CAST(:wardId AS uuid) IS NOT NULL
            """, nativeQuery = true)
    Page<RecruitmentView> searchByProvince(@Param("callerId") UUID callerId, @Param("position") String position,
                                           @Param("now") LocalDateTime now, @Param("provinceId") UUID provinceId,
                                           @Param("wardId") UUID wardId, Pageable pageable);

    /** Chi tiết một tin + cờ người xem; khoảng cách chỉ khi có toạ độ. */
    @Query(value = "SELECT " + MercenarySql.RECRUITMENT_COLUMNS
            + ", CASE WHEN CAST(:lat AS double precision) IS NULL OR CAST(:lng AS double precision) IS NULL"
            + " THEN NULL ELSE " + MercenarySql.DISTANCE_KM + " END AS distanceKm"
            + MercenarySql.RECRUITMENT_FROM + " WHERE r.id = :id", nativeQuery = true)
    Optional<RecruitmentView> findView(@Param("id") UUID id, @Param("callerId") UUID callerId,
                                       @Param("now") LocalDateTime now, @Param("lat") Double lat,
                                       @Param("lng") Double lng);

    /** Context ghi trên một tin (sửa số lượng, đóng, xem ứng viên) — cùng cột chi tiết, dùng dựng response. */
    @Query(value = "SELECT " + MercenarySql.RECRUITMENT_COLUMNS + ", CAST(NULL AS double precision) AS distanceKm"
            + MercenarySql.RECRUITMENT_FROM + " WHERE r.id = :id", nativeQuery = true)
    Optional<RecruitmentWriteContextView> findWriteContext(@Param("id") UUID id, @Param("callerId") UUID callerId,
                                                           @Param("now") LocalDateTime now);

    /**
     * Đổi số lượng cần (CAS trên số đã duyệt / số cần đã đọc); tự tính FILLED / OPEN. 0 dòng = tin vừa đổi.
     */
    @Modifying
    @Query(value = """
            UPDATE mercenary_recruitment
            SET quantity_needed = :quantity,
                status = CASE WHEN quantity_approved = :quantity THEN 'FILLED' ELSE 'OPEN' END,
                updated_at = :now
            WHERE id = :id AND status IN ('OPEN', 'FILLED') AND quantity_approved = :expectedApproved
              AND quantity_needed = :expectedNeeded AND quantity_approved <= :quantity
            """, nativeQuery = true)
    int updateQuantity(@Param("id") UUID id, @Param("quantity") int quantity,
                       @Param("expectedApproved") int expectedApproved, @Param("expectedNeeded") int expectedNeeded,
                       @Param("now") LocalDateTime now);

    /** Đóng tin đang OPEN/FILLED; 0 dòng = tin đã đóng / hết hạn bởi thao tác khác. */
    @Modifying
    @Query(value = """
            UPDATE mercenary_recruitment SET status = 'CLOSED', closed_at = :now, updated_at = :now
            WHERE id = :id AND status IN ('OPEN', 'FILLED')
            """, nativeQuery = true)
    int closeIfActive(@Param("id") UUID id, @Param("now") LocalDateTime now);

    /** Thêm một chỗ đã duyệt (CAS trên số đã đọc, tin phải OPEN); đủ người → FILLED. Khoá dòng tin trước dòng đơn. */
    @Modifying
    @Query(value = """
            UPDATE mercenary_recruitment
            SET quantity_approved = quantity_approved + 1,
                status = CASE WHEN quantity_approved + 1 = quantity_needed THEN 'FILLED' ELSE 'OPEN' END,
                updated_at = :now
            WHERE id = :id AND status = 'OPEN' AND quantity_approved = :expectedApproved
              AND quantity_needed = :expectedNeeded
            """, nativeQuery = true)
    int incrementApproved(@Param("id") UUID id, @Param("expectedApproved") int expectedApproved,
                          @Param("expectedNeeded") int expectedNeeded, @Param("now") LocalDateTime now);

    /** Bớt một chỗ đã duyệt (mercenary rút lui); FILLED → OPEN, giữ CLOSED/EXPIRED. Khoá dòng tin trước dòng đơn. */
    @Modifying
    @Query(value = """
            UPDATE mercenary_recruitment
            SET quantity_approved = quantity_approved - 1,
                status = CASE WHEN status = 'FILLED' THEN 'OPEN' ELSE status END,
                updated_at = :now
            WHERE id = :id
            """, nativeQuery = true)
    int decrementApproved(@Param("id") UUID id, @Param("now") LocalDateTime now);

    /**
     * Xoá tài khoản ứng viên: bớt số đã duyệt của mọi tin có đơn APPROVED của user ở trận đã xác nhận chưa bắt đầu (khoá
     * tin trước đơn). Trả số đã bớt mỗi tin.
     */
    @Query(value = """
            UPDATE mercenary_recruitment rr
            SET quantity_approved = rr.quantity_approved - c.n,
                status = CASE WHEN rr.status = 'FILLED' THEN 'OPEN' ELSE rr.status END,
                updated_at = :now
            FROM (SELECT a.recruitment_id, COUNT(*) AS n
                  FROM mercenary_application a
                  JOIN mercenary_recruitment r ON r.id = a.recruitment_id
                  LEFT JOIN match m ON m.id = r.match_id
                  LEFT JOIN external_match x ON x.id = r.external_match_id
                  WHERE a.applicant_id = :userId AND a.status = 'APPROVED'
                    AND COALESCE(m.status, x.status) IN ('CONFIRMED', 'SCHEDULED')
                    AND COALESCE(m.match_start_time, x.match_start_time) > :now
                  GROUP BY a.recruitment_id) c
            WHERE rr.id = c.recruitment_id
            RETURNING rr.id AS recruitmentId, c.n AS n
            """, nativeQuery = true)
    List<ApprovedCountView> decrementApprovedOfApplicant(@Param("userId") UUID userId,
                                                         @Param("now") LocalDateTime now);

    /** Khoá mọi tin của trận ngoài; trả số đã duyệt mỗi tin đọc sau khi chờ khoá. */
    @Query(value = """
            UPDATE mercenary_recruitment SET updated_at = :now WHERE external_match_id = :externalMatchId
            RETURNING quantity_approved
            """, nativeQuery = true)
    List<Integer> lockByExternalMatch(@Param("externalMatchId") UUID externalMatchId, @Param("now") LocalDateTime now);

    /** Huỷ trận ngoài: đóng mọi tin OPEN/FILLED của trận; trả id tin vừa đóng. */
    @Query(value = """
            UPDATE mercenary_recruitment SET status = 'CLOSED', closed_at = :now, updated_at = :now
            WHERE external_match_id = :externalMatchId AND status IN ('OPEN', 'FILLED')
            RETURNING id
            """, nativeQuery = true)
    List<UUID> closeActiveByExternalMatch(@Param("externalMatchId") UUID externalMatchId,
                                          @Param("now") LocalDateTime now);

    /**
     * Job: nhận một lô tin OPEN/FILLED của trận bị huỷ hoặc đã tới giờ kết thúc; khoá dòng, bỏ qua dòng instance khác
     * đang giữ.
     */
    @Query(value = """
            SELECT r.id AS id, r.match_id AS matchId, r.external_match_id AS externalMatchId, r.team_id AS teamId,
                   CASE WHEN COALESCE(m.status, x.status) = 'CANCELLED' THEN 'MATCH_CANCELLED' ELSE 'EXPIRED' END
                       AS cause
            FROM mercenary_recruitment r
            LEFT JOIN match m ON m.id = r.match_id
            LEFT JOIN external_match x ON x.id = r.external_match_id
            WHERE r.status IN ('OPEN', 'FILLED')
              AND (COALESCE(m.status, x.status) = 'CANCELLED' OR COALESCE(m.match_end_time, x.match_end_time) <= :now)
            ORDER BY r.id
            LIMIT :limit
            FOR NO KEY UPDATE OF r SKIP LOCKED
            """, nativeQuery = true)
    List<DueRecruitmentView> claimDue(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /** Job: ghi CLOSED (trận huỷ) / EXPIRED cho lô đã nhận. */
    @Modifying
    @Query(value = """
            UPDATE mercenary_recruitment
            SET status = CASE WHEN id = ANY(CAST(:closedIds AS uuid[])) THEN 'CLOSED' ELSE 'EXPIRED' END,
                closed_at = CASE WHEN id = ANY(CAST(:closedIds AS uuid[])) THEN CAST(:now AS timestamp) END,
                updated_at = :now
            WHERE (id = ANY(CAST(:closedIds AS uuid[])) OR id = ANY(CAST(:expiredIds AS uuid[])))
              AND status IN ('OPEN', 'FILLED')
            """, nativeQuery = true)
    int markClosedOrExpired(@Param("closedIds") UUID[] closedIds, @Param("expiredIds") UUID[] expiredIds,
                            @Param("now") LocalDateTime now);
}
