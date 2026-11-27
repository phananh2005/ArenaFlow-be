package com.phananh.arenaflow.domain.repository;

/**
 * Hằng SQL dùng chung của tuyển mercenary — một nguồn cho cách đọc "trận của tin" (kèo {@code match m} hoặc trận ngoài
 * hệ thống {@code external_match x}, đúng một trong hai khác null), trạng thái hiệu lực của tin / đơn, điều kiện ứng
 * viên và thống kê cá nhân. Mọi hằng là compile-time constant để ghép vào {@code @Query}.
 * <p>Quy ước alias: tin {@code r}, kèo {@code m}, trận ngoài {@code x}, đơn {@code a}, user của thống kê {@code su};
 * thời điểm hiện tại là tham số {@code :now}.
 */
public final class MercenarySql {

    /** Join trận của tin {@code r}. */
    public static final String GAME_JOIN = """
             LEFT JOIN match m ON m.id = r.match_id
             LEFT JOIN external_match x ON x.id = r.external_match_id
            """;

    public static final String GAME_START = "COALESCE(m.match_start_time, x.match_start_time)";
    public static final String GAME_END = "COALESCE(m.match_end_time, x.match_end_time)";
    public static final String GAME_VENUE = "COALESCE(m.venue_id, x.venue_id)";
    public static final String GAME_SKILL = "COALESCE(m.skill_levels, x.skill_levels)";
    /** Trạng thái kèo, hoặc {@code SCHEDULED}/{@code CANCELLED} của trận ngoài. */
    public static final String GAME_STATUS = "COALESCE(m.status, x.status)";
    public static final String GAME_SOURCE = "CASE WHEN r.match_id IS NOT NULL THEN 'SYSTEM' ELSE 'EXTERNAL' END";
    public static final String GAME_CANCELLED = "(COALESCE(m.status, x.status) = 'CANCELLED')";
    /** Kèo đã ghép hoặc trận ngoài đang hiệu lực. */
    public static final String GAME_CONFIRMED = "(COALESCE(m.status, x.status) IN ('CONFIRMED', 'SCHEDULED'))";
    /** Trận đã kết thúc (được đánh giá / report "đã tham gia"): kèo ARCHIVED/COMPLETED; trận ngoài chưa huỷ, qua giờ kết thúc. */
    public static final String GAME_ENDED = """
            COALESCE(m.status IN ('ARCHIVED', 'COMPLETED'), x.status = 'SCHEDULED' AND x.match_end_time <= :now, false)""";

    /** Trạng thái hiệu lực của tin: trận huỷ → CLOSED; qua giờ kết thúc → EXPIRED. */
    public static final String RECRUITMENT_EFFECTIVE_STATUS = "CASE WHEN r.status IN ('OPEN', 'FILLED') AND "
            + GAME_CANCELLED + " THEN 'CLOSED' WHEN r.status IN ('OPEN', 'FILLED') AND " + GAME_END
            + " <= :now THEN 'EXPIRED' ELSE r.status END";

    /** Trạng thái hiệu lực của đơn: PENDING của tin hiệu lực CLOSED/EXPIRED → REJECTED. */
    public static final String APPLICATION_EFFECTIVE_STATUS = "CASE WHEN a.status = 'PENDING' AND ("
            + RECRUITMENT_EFFECTIVE_STATUS + ") IN ('CLOSED', 'EXPIRED') THEN 'REJECTED' ELSE a.status END";

    private static final String OVERLAP_PREFIX = """
            EXISTS (SELECT 1 FROM mercenary_application a2
                    JOIN mercenary_recruitment r2 ON r2.id = a2.recruitment_id
                    LEFT JOIN match m2 ON m2.id = r2.match_id
                    LEFT JOIN external_match x2 ON x2.id = r2.external_match_id
                    WHERE a2.status = 'APPROVED'
                      AND COALESCE(m2.status, x2.status) <> 'CANCELLED'
                      AND COALESCE(m2.match_start_time, x2.match_start_time) < COALESCE(m.match_end_time, x.match_end_time)
                      AND COALESCE(m2.match_end_time, x2.match_end_time) > COALESCE(m.match_start_time, x.match_start_time)
                      AND a2.applicant_id = """;

    /** Người gọi đã có đơn APPROVED ở trận trùng giờ (gồm chính trận — tối đa một đơn được duyệt mỗi trận). */
    public static final String OVERLAPPING_APPROVAL_OF_CALLER = OVERLAP_PREFIX + " :callerId)";
    /** Như trên cho ứng viên của đơn {@code a}. */
    public static final String OVERLAPPING_APPROVAL_OF_APPLICANT = OVERLAP_PREFIX + " a.applicant_id)";

    private static final String MEMBER_PREFIX = """
            EXISTS (SELECT 1 FROM team_member tm
                    WHERE tm.status = 'ACTIVE'
                      AND tm.team_id IN (m.author_team_id, m.confirmed_opponent_team_id, x.team_id)
                      AND tm.user_id = """;

    /** Người gọi là thành viên ACTIVE của một đội trong trận (kèo: hai đội; trận ngoài: đội khai báo). */
    public static final String MEMBER_OF_GAME_TEAMS_CALLER = MEMBER_PREFIX + " :callerId)";
    /** Như trên cho ứng viên của đơn {@code a}. */
    public static final String MEMBER_OF_GAME_TEAMS_APPLICANT = MEMBER_PREFIX + " a.applicant_id)";

    /** Khoảng cách (km) từ toạ độ {@code :lat}/{@code :lng} tới sân {@code v} (Haversine). */
    public static final String DISTANCE_KM = """
            6371 * 2 * ASIN(SQRT(POWER(SIN(RADIANS(v.latitude - CAST(:lat AS double precision)) / 2), 2)
                + COS(RADIANS(CAST(:lat AS double precision))) * COS(RADIANS(v.latitude))
                * POWER(SIN(RADIANS(v.longitude - CAST(:lng AS double precision)) / 2), 2)))""";

    /**
     * Thống kê cá nhân của user {@code su} (subquery cho {@code CROSS JOIN LATERAL}): hoàn thành = được đội trưởng chấm
     * sao và không bị report bùng (chưa bác); trận được tính = chấm sao, hoặc đã kết thúc + bị report bùng, hoặc rút lui
     * sau duyệt trong cửa sổ {@code :cancelWindowHours} trước giờ bắt đầu. Trận bị huỷ không tính. Sao trung bình chỉ tính
     * sao đội trưởng chấm.
     */
    public static final String USER_STATS_LATERAL = """
            SELECT st.completed_matches, st.counted_matches, rs.avg_rating, rs.rating_count
            FROM (
                SELECT COUNT(DISTINCT f.game_id) FILTER (WHERE f.app_status = 'APPROVED' AND f.rated_star AND NOT f.noshow)
                           AS completed_matches,
                       COUNT(DISTINCT f.game_id) FILTER (WHERE (f.app_status = 'APPROVED' AND NOT f.cancelled
                                                                AND (f.rated_star OR (f.ended AND f.noshow)))
                                                           OR (f.app_status = 'WITHDRAWN' AND NOT f.cancelled
                                                               AND f.withdrawn_at > f.start_time
                                                                   - make_interval(hours => :cancelWindowHours)))
                           AS counted_matches
                FROM (
                    SELECT p.*,
                           EXISTS (SELECT 1 FROM rating rt WHERE rt.application_id = p.application_id
                                   AND rt.rater_type = 'TEAM' AND rt.score IS NOT NULL) AS rated_star,
                           EXISTS (SELECT 1 FROM report rp
                                   WHERE (rp.match_id = p.game_id OR rp.external_match_id = p.game_id)
                                     AND rp.reported_type = 'INDIVIDUAL' AND rp.reported_id = su.id
                                     AND rp.report_type = 'NOSHOW' AND rp.status <> 'REJECTED') AS noshow
                    FROM (
                        SELECT a.id AS application_id, COALESCE(r.match_id, r.external_match_id) AS game_id,
                               COALESCE(m.match_start_time, x.match_start_time) AS start_time,
                               (COALESCE(m.status, x.status) = 'CANCELLED') AS cancelled,
                               COALESCE(m.status IN ('ARCHIVED', 'COMPLETED'),
                                        x.status = 'SCHEDULED' AND x.match_end_time <= :now, false) AS ended,
                               a.status AS app_status, a.withdrawn_at
                        FROM mercenary_application a
                        JOIN mercenary_recruitment r ON r.id = a.recruitment_id
                        LEFT JOIN match m ON m.id = r.match_id
                        LEFT JOIN external_match x ON x.id = r.external_match_id
                        WHERE a.applicant_id = su.id AND a.responded_at IS NOT NULL
                          AND a.status IN ('APPROVED', 'WITHDRAWN')
                    ) p
                ) f
            ) st
            CROSS JOIN (
                SELECT AVG(rt.score) AS avg_rating, COUNT(rt.score) AS rating_count
                FROM rating rt
                WHERE rt.ratee_type = 'INDIVIDUAL' AND rt.ratee_id = su.id AND rt.score IS NOT NULL
            ) rs
            """;

    /**
     * Cột chung của tin tuyển cho mọi màn đọc (danh sách đội, tìm kiếm, chi tiết, context ghi). Cần {@link #RECRUITMENT_FROM}.
     */
    public static final String RECRUITMENT_COLUMNS = """
            r.id AS recruitmentId, r.match_id AS matchId, r.external_match_id AS externalMatchId,
            t.id AS teamId, t.name AS teamName, t.avatar_url AS teamAvatarUrl, t.status AS teamStatus,
            r.position AS position, r.quantity_needed AS quantityNeeded, r.quantity_approved AS quantityApproved,
            r.status AS status,
            """ + RECRUITMENT_EFFECTIVE_STATUS + " AS effectiveStatus, " + GAME_SOURCE + " AS source, "
            + "x.opponent_name AS opponentName, " + GAME_STATUS + " AS gameStatus, " + GAME_START + " AS gameStart, "
            + GAME_END + " AS gameEnd, m.format AS format, " + GAME_SKILL + " AS skillLevels," + """

            v.id AS venueId, v.name AS venueName, v.address AS venueAddress, v.latitude AS venueLatitude,
            v.longitude AS venueLongitude, vw.name AS wardName, vp.name AS provinceName,
            cu.display_name AS captainName, (cu.deleted_at IS NOT NULL) AS captainDeleted,
            COALESCE(cu.id = :callerId, false) AS viewerIsCaptain,
            ma.id AS myApplicationId, ma.status AS myApplicationStatus, mc.id AS myConversationId,
            (SELECT COUNT(*) FROM mercenary_application pa WHERE pa.recruitment_id = r.id AND pa.status = 'PENDING')
                AS pendingCount,
            r.closed_at AS closedAt, r.created_at AS createdAt, r.updated_at AS updatedAt
            """;

    /** FROM + join của {@link #RECRUITMENT_COLUMNS}: trận, đội, sân, khu vực, đội trưởng hiện tại, đơn đang hoạt động của người gọi. */
    public static final String RECRUITMENT_FROM = " FROM mercenary_recruitment r " + GAME_JOIN + """
             JOIN team t ON t.id = r.team_id
             LEFT JOIN venue v ON v.id = COALESCE(m.venue_id, x.venue_id)
             LEFT JOIN administrative_unit vw ON vw.id = v.ward_id
             LEFT JOIN administrative_unit vp ON vp.id = vw.parent_id
             LEFT JOIN team_member cm ON cm.team_id = t.id AND cm.role = 'CAPTAIN' AND cm.status = 'ACTIVE'
             LEFT JOIN "user" cu ON cu.id = cm.user_id
             LEFT JOIN LATERAL (SELECT a.id, a.status FROM mercenary_application a
                                WHERE a.recruitment_id = r.id AND a.applicant_id = :callerId
                                  AND a.status IN ('PENDING', 'APPROVED')
                                ORDER BY a.applied_at DESC, a.id DESC LIMIT 1) ma ON true
             LEFT JOIN conversation mc ON mc.application_id = ma.id
            """;

    /**
     * Cột của đơn ứng tuyển cho màn đọc / context ghi (2.9–2.13): đơn + ứng viên + tin + trận + đội + đội trưởng hiện tại +
     * sân + khu vực + cuộc trò chuyện. Cần {@link #APPLICATION_FROM}.
     */
    public static final String APPLICATION_COLUMNS = "a.id AS applicationId, a.status AS status, "
            + APPLICATION_EFFECTIVE_STATUS + " AS effectiveStatus," + """

            a.applicant_id AS applicantId, ap.display_name AS applicantName, ap.avatar_url AS applicantAvatarUrl,
            (ap.deleted_at IS NOT NULL) AS applicantDeleted, ap.preferred_position AS applicantPreferredPosition,
            a.applied_at AS appliedAt, a.responded_at AS respondedAt, a.withdrawn_at AS withdrawnAt,
            r.id AS recruitmentId, r.position AS position, r.status AS recruitmentStatus,
            """ + RECRUITMENT_EFFECTIVE_STATUS + " AS recruitmentEffectiveStatus," + """

            r.quantity_needed AS quantityNeeded, r.quantity_approved AS quantityApproved,
            r.match_id AS matchId, r.external_match_id AS externalMatchId,
            """ + GAME_SOURCE + " AS source, x.opponent_name AS opponentName, " + GAME_STATUS + " AS gameStatus, "
            + GAME_START + " AS gameStart, " + GAME_END + " AS gameEnd," + """

            t.id AS teamId, t.name AS teamName, t.avatar_url AS teamAvatarUrl, t.status AS teamStatus,
            cu.display_name AS captainName, (cu.deleted_at IS NOT NULL) AS captainDeleted,
            v.id AS venueId, v.name AS venueName, v.address AS venueAddress, v.latitude AS venueLatitude,
            v.longitude AS venueLongitude, vw.name AS wardName, vp.name AS provinceName,
            c.id AS conversationId
            """;

    /** FROM + join của {@link #APPLICATION_COLUMNS}. */
    public static final String APPLICATION_FROM = """
             FROM mercenary_application a
             JOIN mercenary_recruitment r ON r.id = a.recruitment_id
            """ + GAME_JOIN + """
             JOIN team t ON t.id = r.team_id
             JOIN "user" ap ON ap.id = a.applicant_id
             LEFT JOIN venue v ON v.id = COALESCE(m.venue_id, x.venue_id)
             LEFT JOIN administrative_unit vw ON vw.id = v.ward_id
             LEFT JOIN administrative_unit vp ON vp.id = vw.parent_id
             LEFT JOIN team_member cm ON cm.team_id = t.id AND cm.role = 'CAPTAIN' AND cm.status = 'ACTIVE'
             LEFT JOIN "user" cu ON cu.id = cm.user_id
             LEFT JOIN conversation c ON c.application_id = a.id
            """;

    /**
     * Khối cột {@code MERCENARY_APPLICATION} cho câu ngữ cảnh cuộc trò chuyện của lõi chat (ghép cùng
     * {@link #CONVERSATION_MERCENARY_JOINS}); cuộc trò chuyện alias {@code c}, tham số {@code :callerId}, {@code :now}.
     * Cột của dòng khác loại ngữ cảnh là null.
     */
    public static final String CONVERSATION_MERCENARY_COLUMNS = """
            c.application_id AS applicationId, c.external_match_id AS externalMatchId,
            """ + "CASE WHEN a.id IS NULL THEN NULL ELSE " + APPLICATION_EFFECTIVE_STATUS
            + " END AS applicationEffectiveStatus," + """

            mt.name AS mercenaryTeamName, mt.avatar_url AS mercenaryTeamAvatarUrl, mt.status AS mercenaryTeamStatus,
            mcu.display_name AS mercenaryCaptainName, (mcu.deleted_at IS NOT NULL) AS mercenaryCaptainDeleted,
            mcm.user_id AS mercenaryCaptainUserId, mt.disbanded_by AS mercenaryTeamDisbandedBy,
            ap.display_name AS applicantName, ap.avatar_url AS applicantAvatarUrl,
            (ap.deleted_at IS NOT NULL) AS applicantDeleted,
            COALESCE(mcm.user_id = :callerId, false) AS callerMercenaryCaptain,
            COALESCE(mt.disbanded_by = :callerId, false) AS callerMercenaryDisbandedBy,
            COALESCE(ap.id = :callerId, false) AS callerApplicant
            """;

    /** Join của khối {@code MERCENARY_APPLICATION} (chỉ khớp dòng có {@code context_type = 'MERCENARY_APPLICATION'}). */
    public static final String CONVERSATION_MERCENARY_JOINS = """
             LEFT JOIN mercenary_application a ON c.context_type = 'MERCENARY_APPLICATION' AND a.id = c.application_id
             LEFT JOIN mercenary_recruitment r ON r.id = a.recruitment_id
             LEFT JOIN match m ON m.id = r.match_id
             LEFT JOIN external_match x ON x.id = r.external_match_id
             LEFT JOIN team mt ON c.context_type = 'MERCENARY_APPLICATION' AND mt.id = c.party_a_id
             LEFT JOIN team_member mcm ON mcm.team_id = mt.id AND mcm.role = 'CAPTAIN' AND mcm.status = 'ACTIVE'
             LEFT JOIN "user" mcu ON mcu.id = mcm.user_id
             LEFT JOIN "user" ap ON c.context_type = 'MERCENARY_APPLICATION' AND ap.id = c.party_b_id
            """;

    private MercenarySql() {
    }
}
