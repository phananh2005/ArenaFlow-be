package com.phananh.arenaflow.domain.repository;

/**
 * Khối SQL ngữ cảnh cuộc trò chuyện viết một lần, dùng chung cho chi tiết một cuộc trò chuyện và danh sách "Tin nhắn
 * của tôi" (alias bảng gốc là {@code c}; tham số {@code :callerId}, {@code :now}). Mỗi loại ngữ cảnh join có điều kiện
 * {@code c.context_type}; các join theo khoá chính / index nên không đổi số câu.
 */
public final class ConversationSql {

    /** Yêu cầu PENDING / thương lượng NEGOTIATING của kèo OPEN đã tới hạn hiển thị EXPIRED. */
    public static final String CONTEXT_COLUMNS = """
            c.id AS id, c.context_type AS contextType, c.match_id AS matchId, c.party_a_type AS partyAType,
            c.party_a_id AS partyAId, c.party_b_type AS partyBType, c.party_b_id AS partyBId,
            me.display_name AS callerName, me.avatar_url AS callerAvatarUrl,
            (me.id IS NULL OR me.deleted_at IS NOT NULL) AS callerDeleted,
            m.type AS matchType, m.status AS matchStatus, m.expires_at AS expiresAt, m.author_team_id AS authorTeamId,
            m.confirmed_opponent_team_id AS matchOpponentTeamId,
            ta.name AS partyAName, ta.avatar_url AS partyAAvatarUrl, ua.display_name AS partyACaptainName,
            (ua.deleted_at IS NOT NULL) AS partyACaptainDeleted, COALESCE(ca.user_id = :callerId, false) AS callerPartyACaptain,
            ta.status AS partyATeamStatus, COALESCE(ta.disbanded_by = :callerId, false) AS callerPartyADisbandedBy,
            ca.user_id AS partyACaptainUserId, ta.disbanded_by AS partyADisbandedBy,
            tb.name AS partyBName, tb.avatar_url AS partyBAvatarUrl, ub.display_name AS partyBCaptainName,
            (ub.deleted_at IS NOT NULL) AS partyBCaptainDeleted, COALESCE(cb.user_id = :callerId, false) AS callerPartyBCaptain,
            tb.status AS partyBTeamStatus, COALESCE(tb.disbanded_by = :callerId, false) AS callerPartyBDisbandedBy,
            cb.user_id AS partyBCaptainUserId, tb.disbanded_by AS partyBDisbandedBy,
            lr.id AS requestId, lr.status AS requestStatus,
            CASE WHEN lr.status = 'PENDING' AND m.status = 'OPEN' AND m.expires_at <= :now THEN 'EXPIRED'
                 ELSE lr.status END AS requestEffectiveStatus,
            pr.id AS pairingId, pr.status AS pairingStatus,
            CASE WHEN pr.status = 'NEGOTIATING' AND m.status = 'OPEN' AND m.expires_at <= :now THEN 'EXPIRED'
                 ELSE pr.status END AS pairingEffectiveStatus
            """;

    /**
     * Yêu cầu GẦN NHẤT của đội tham gia (luồng A): mỗi đội chỉ có một yêu cầu đang hiệu lực mỗi kèo, yêu cầu cũ chỉ có
     * thể WITHDRAWN / EXPIRED nên yêu cầu gần nhất là yêu cầu quyết định quyền nhắn. Thương lượng của đội tham gia
     * (luồng B): mỗi (kèo, đội) tối đa một pairing — sau SUPERSEDED / EXPIRED kèo không còn OPEN để mở thương lượng mới.
     */
    public static final String CONTEXT_JOINS = """
            LEFT JOIN "user" me ON me.id = :callerId
            LEFT JOIN match m ON c.context_type = 'TEAM_MATCH' AND m.id = c.match_id
            LEFT JOIN team ta ON c.context_type = 'TEAM_MATCH' AND ta.id = c.party_a_id
            LEFT JOIN team_member ca ON ca.team_id = ta.id AND ca.role = 'CAPTAIN' AND ca.status = 'ACTIVE'
            LEFT JOIN "user" ua ON ua.id = ca.user_id
            LEFT JOIN team tb ON c.context_type = 'TEAM_MATCH' AND tb.id = c.party_b_id
            LEFT JOIN team_member cb ON cb.team_id = tb.id AND cb.role = 'CAPTAIN' AND cb.status = 'ACTIVE'
            LEFT JOIN "user" ub ON ub.id = cb.user_id
            LEFT JOIN LATERAL (SELECT r.id, r.status FROM match_request r
                               WHERE m.type = 'HAS_VENUE' AND r.match_id = c.match_id
                                 AND r.requester_team_id = c.party_b_id
                               ORDER BY r.requested_at DESC, r.id DESC LIMIT 1) lr ON true
            LEFT JOIN pairing pr ON m.type = 'NEED_VENUE' AND pr.match_id = c.match_id
                                AND pr.interested_team_id = c.party_b_id
            """;

    /** Cuộc trò chuyện người gọi là một bên: đội trưởng ACTIVE / người đã giải tán đội của một bên, hoặc bên B là chính họ. */
    public static final String MINE_CTE = """
            WITH my_teams AS (
                SELECT tm.team_id FROM team_member tm
                WHERE tm.user_id = :callerId AND tm.role = 'CAPTAIN' AND tm.status = 'ACTIVE'
                UNION SELECT t.id FROM team t WHERE t.disbanded_by = :callerId AND t.status = 'DISBANDED'),
            mine AS (
                SELECT c.id FROM conversation c JOIN my_teams mt ON c.party_a_type = 'TEAM' AND c.party_a_id = mt.team_id
                UNION SELECT c.id FROM conversation c JOIN my_teams mt ON c.party_b_type = 'TEAM' AND c.party_b_id = mt.team_id
                UNION SELECT c.id FROM conversation c WHERE c.party_b_type = 'USER' AND c.party_b_id = :callerId)
            """;

    private ConversationSql() {
    }
}
