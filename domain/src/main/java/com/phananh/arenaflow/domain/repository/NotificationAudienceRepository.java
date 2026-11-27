package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.entity.chat.Notification;
import com.phananh.arenaflow.domain.projection.ApplicationAudienceRowView;
import com.phananh.arenaflow.domain.projection.GameAudienceRowView;
import com.phananh.arenaflow.domain.projection.InvitationAudienceView;
import com.phananh.arenaflow.domain.projection.MatchAudienceRowView;
import com.phananh.arenaflow.domain.projection.ReportAudienceRowView;
import com.phananh.arenaflow.domain.projection.TeamAudienceRowView;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Người nhận thông báo và dữ liệu hiển thị, đọc tại lúc tạo thông báo. Mỗi method đúng một câu, số dòng không vượt số
 * người nhận của một sự kiện. "Đội trưởng" = đội trưởng ACTIVE hiện tại; đội không còn đội trưởng → cột null.
 */
public interface NotificationAudienceRepository extends Repository<Notification, UUID> {

    /**
     * Các đội nhận thông báo của một kèo: đội trong {@code teamIds}, đội có yêu cầu ở trạng thái {@code requestStatus},
     * đội có thương lượng ở trạng thái {@code pairingStatus} (tham số null → nhánh đó không trả dòng). {@code hasRated}:
     * đội đó đã đánh giá trận (đánh giá giữa hai đội).
     */
    @Query(value = """
            WITH g AS (SELECT m.id, m.type, m.status, m.match_start_time, m.author_team_id, m.confirmed_opponent_team_id,
                              v.name AS venue_name, at.name AS author_team_name
                       FROM match m JOIN team at ON at.id = m.author_team_id
                       LEFT JOIN venue v ON v.id = COALESCE(m.confirmed_venue_id, m.venue_id)
                       WHERE m.id = :matchId),
                 aud AS (SELECT x.team_id, 'LISTED' AS source FROM unnest(CAST(:teamIds AS uuid[])) AS x(team_id)
                         UNION ALL
                         SELECT r.requester_team_id, 'REQUEST' FROM match_request r
                         WHERE r.match_id = :matchId AND r.status = CAST(:requestStatus AS varchar)
                         UNION ALL
                         SELECT p.interested_team_id, 'PAIRING' FROM pairing p
                         WHERE p.match_id = :matchId AND p.status = CAST(:pairingStatus AS varchar))
            SELECT g.id AS matchId, g.type AS matchType, g.status AS matchStatus, g.match_start_time AS matchStartTime,
                   g.venue_name AS venueName, g.author_team_id AS authorTeamId, g.author_team_name AS authorTeamName,
                   g.confirmed_opponent_team_id AS opponentTeamId, a.source AS source, a.team_id AS teamId,
                   t.name AS teamName, cm.user_id AS captainUserId,
                   EXISTS (SELECT 1 FROM rating rt WHERE rt.match_id = g.id AND rt.rater_type = 'TEAM'
                           AND rt.rater_id = a.team_id AND rt.application_id IS NULL) AS hasRated
            FROM g CROSS JOIN aud a
            JOIN team t ON t.id = a.team_id
            LEFT JOIN team_member cm ON cm.team_id = a.team_id AND cm.role = 'CAPTAIN' AND cm.status = 'ACTIVE'
            """, nativeQuery = true)
    List<MatchAudienceRowView> findMatchAudience(@Param("matchId") UUID matchId, @Param("teamIds") UUID[] teamIds,
                                                 @Param("requestStatus") String requestStatus,
                                                 @Param("pairingStatus") String pairingStatus);

    /** Đơn ứng tuyển + tin + đội tuyển + đội trưởng hiện tại + trận (kèo hoặc trận ngoài) + cờ đã đánh giá hai bên. */
    @Query(value = """
            SELECT a.id AS applicationId, a.status AS applicationStatus, a.applicant_id AS applicantId,
                   r.position AS position, r.id AS recruitmentId, r.team_id AS teamId, t.name AS teamName,
                   cm.user_id AS captainUserId, r.match_id AS matchId, r.external_match_id AS externalMatchId,
                   """ + MercenarySql.GAME_START + """
                    AS gameStartTime, v.name AS venueName,
                   EXISTS (SELECT 1 FROM rating rt WHERE rt.application_id = a.id AND rt.rater_type = 'TEAM') AS ratedByTeam,
                   EXISTS (SELECT 1 FROM rating rt WHERE rt.application_id = a.id AND rt.rater_type = 'INDIVIDUAL')
                       AS ratedByApplicant
            FROM mercenary_application a
            JOIN mercenary_recruitment r ON r.id = a.recruitment_id
            """ + MercenarySql.GAME_JOIN + """
            JOIN team t ON t.id = r.team_id
            LEFT JOIN team_member cm ON cm.team_id = r.team_id AND cm.role = 'CAPTAIN' AND cm.status = 'ACTIVE'
            LEFT JOIN venue v ON v.id = """ + MercenarySql.GAME_VENUE + """

            WHERE a.id = ANY(CAST(:applicationIds AS uuid[]))
            """, nativeQuery = true)
    List<ApplicationAudienceRowView> findApplicationAudience(@Param("applicationIds") UUID[] applicationIds);

    /**
     * Người nhận của một report: bên report (REPORTER), bên bị report (REPORTED) — đội thì đội trưởng hiện tại, cá nhân
     * thì chính người đó — và mọi Admin đang hoạt động (ADMIN) khi {@code includeAdmins}. Mỗi dòng mang dữ liệu
     * hiển thị của report, tranh chấp (tối đa một mỗi report) và trận.
     */
    @Query(value = """
            WITH rp AS (
                SELECT r.id AS report_id, r.report_type, r.reporter_type, r.reporter_id, r.reported_type, r.reported_id,
                       r.application_id, d.id AS dispute_id, d.decision,
                       rt.name AS reporter_team_name, dt.name AS reported_team_name, rc.position,
                       COALESCE(m.match_start_time, x.match_start_time) AS game_start_time
                FROM report r
                LEFT JOIN dispute d ON d.report_id = r.id
                LEFT JOIN team rt ON r.reporter_type = 'TEAM' AND rt.id = r.reporter_id
                LEFT JOIN team dt ON r.reported_type = 'TEAM' AND dt.id = r.reported_id
                LEFT JOIN mercenary_application ma ON ma.id = r.application_id
                LEFT JOIN mercenary_recruitment rc ON rc.id = ma.recruitment_id
                LEFT JOIN match m ON m.id = r.match_id
                LEFT JOIN external_match x ON x.id = r.external_match_id
                WHERE r.id = :reportId),
            aud AS (
                SELECT 'REPORTER' AS audience,
                       CASE WHEN rp.reporter_type = 'TEAM'
                            THEN (SELECT tm.user_id FROM team_member tm WHERE tm.team_id = rp.reporter_id
                                  AND tm.role = 'CAPTAIN' AND tm.status = 'ACTIVE')
                            ELSE rp.reporter_id END AS recipient_user_id
                FROM rp
                UNION ALL
                SELECT 'REPORTED',
                       CASE WHEN rp.reported_type = 'TEAM'
                            THEN (SELECT tm.user_id FROM team_member tm WHERE tm.team_id = rp.reported_id
                                  AND tm.role = 'CAPTAIN' AND tm.status = 'ACTIVE')
                            ELSE rp.reported_id END
                FROM rp
                UNION ALL
                SELECT 'ADMIN', u.id FROM "user" u
                WHERE :includeAdmins AND u.role = 'ADMIN' AND u.deleted_at IS NULL)
            SELECT aud.audience AS audience, aud.recipient_user_id AS recipientUserId, rp.report_id AS reportId,
                   rp.report_type AS reportType, rp.reporter_type AS reporterType, rp.reported_type AS reportedType,
                   rp.reporter_team_name AS reporterTeamName, rp.reported_team_name AS reportedTeamName,
                   rp.position AS position, rp.application_id AS applicationId, rp.dispute_id AS disputeId,
                   rp.decision AS decision, rp.game_start_time AS gameStartTime
            FROM aud CROSS JOIN rp
            """, nativeQuery = true)
    List<ReportAudienceRowView> findReportAudience(@Param("reportId") UUID reportId,
                                                   @Param("includeAdmins") boolean includeAdmins);

    @Query(value = """
            SELECT t.id AS teamId, t.name AS teamName, cm.user_id AS captainUserId
            FROM team t
            LEFT JOIN team_member cm ON cm.team_id = t.id AND cm.role = 'CAPTAIN' AND cm.status = 'ACTIVE'
            WHERE t.id = ANY(CAST(:teamIds AS uuid[]))
            """, nativeQuery = true)
    List<TeamAudienceRowView> findTeamAudience(@Param("teamIds") UUID[] teamIds);

    @Query(value = """
            SELECT tm.status AS invitationStatus, tm.user_id AS inviteeUserId, t.name AS teamName
            FROM team_member tm JOIN team t ON t.id = tm.team_id
            WHERE tm.id = :invitationId
            """, nativeQuery = true)
    Optional<InvitationAudienceView> findInvitationAudience(@Param("invitationId") UUID invitationId);

    @Query(value = "SELECT id FROM \"user\" WHERE role = 'ADMIN' AND deleted_at IS NULL", nativeQuery = true)
    List<UUID> findActiveAdminIds();

    /**
     * Người nhận nhắc của các trận: (1) đội trưởng hai đội của kèo trong {@code matchIds} có trạng thái thuộc
     * {@code matchStatuses}; (2) mercenary đã duyệt của các kèo đó; (3) mercenary đã duyệt của trận ngoài
     * {@code externalMatchIds} còn SCHEDULED. Ba nhánh tách theo cột khoá trận (không OR) để dùng index riêng.
     */
    @Query(value = """
            SELECT 'CAPTAIN' AS kind, cm.user_id AS recipientUserId, m.id AS matchId, CAST(NULL AS uuid) AS externalMatchId,
                   CAST(NULL AS uuid) AS applicationId, m.match_start_time AS startTime, v.name AS venueName,
                   t.name AS teamName, o.name AS opponentTeamName, CAST(NULL AS varchar) AS position
            FROM match m
            CROSS JOIN LATERAL (VALUES (m.author_team_id, m.confirmed_opponent_team_id),
                                       (m.confirmed_opponent_team_id, m.author_team_id)) AS s(team_id, opponent_id)
            JOIN team t ON t.id = s.team_id
            JOIN team o ON o.id = s.opponent_id
            JOIN team_member cm ON cm.team_id = s.team_id AND cm.role = 'CAPTAIN' AND cm.status = 'ACTIVE'
            LEFT JOIN venue v ON v.id = COALESCE(m.confirmed_venue_id, m.venue_id)
            WHERE m.id = ANY(CAST(:matchIds AS uuid[])) AND m.status = ANY(CAST(:matchStatuses AS text[]))
            UNION ALL
            SELECT 'MERCENARY', a.applicant_id, m.id, CAST(NULL AS uuid), a.id, m.match_start_time, v.name,
                   t.name, CAST(NULL AS varchar), r.position
            FROM mercenary_recruitment r
            JOIN mercenary_application a ON a.recruitment_id = r.id AND a.status = 'APPROVED'
            JOIN match m ON m.id = r.match_id
            JOIN team t ON t.id = r.team_id
            LEFT JOIN venue v ON v.id = COALESCE(m.confirmed_venue_id, m.venue_id)
            WHERE r.match_id = ANY(CAST(:matchIds AS uuid[])) AND m.status = ANY(CAST(:matchStatuses AS text[]))
            UNION ALL
            SELECT 'MERCENARY', a.applicant_id, CAST(NULL AS uuid), x.id, a.id, x.match_start_time, v.name,
                   t.name, CAST(NULL AS varchar), r.position
            FROM mercenary_recruitment r
            JOIN mercenary_application a ON a.recruitment_id = r.id AND a.status = 'APPROVED'
            JOIN external_match x ON x.id = r.external_match_id
            JOIN team t ON t.id = r.team_id
            LEFT JOIN venue v ON v.id = x.venue_id
            WHERE r.external_match_id = ANY(CAST(:externalMatchIds AS uuid[])) AND x.status = 'SCHEDULED'
            """, nativeQuery = true)
    List<GameAudienceRowView> findGameAudience(@Param("matchIds") UUID[] matchIds,
                                               @Param("matchStatuses") String[] matchStatuses,
                                               @Param("externalMatchIds") UUID[] externalMatchIds);
}
