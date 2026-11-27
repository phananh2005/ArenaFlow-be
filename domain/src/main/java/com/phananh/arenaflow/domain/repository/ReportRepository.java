package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.entity.rating.Report;
import com.phananh.arenaflow.domain.projection.MercenaryReportContextView;
import com.phananh.arenaflow.domain.projection.MercenaryReportView;
import com.phananh.arenaflow.domain.projection.ReportDecisionView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Report sau trận / qua chat (đội ↔ đội, đội ↔ mercenary). */
public interface ReportRepository extends JpaRepository<Report, UUID> {

    String SUBJECT_TEAM = "TEAM";
    String DIRECTION_RECEIVED = "RECEIVED";

    String MERCENARY_REPORT_SELECT = """
            SELECT rp.id AS reportId, rp.application_id AS applicationId, rp.match_id AS matchId,
                   rp.external_match_id AS externalMatchId,
                   COALESCE(m.match_start_time, x.match_start_time) AS gameStart,
                   rp.reporter_type AS reporterType, rp.report_type AS reportType, rp.reason AS reason,
                   rp.status AS status, rp.reliability_penalty AS reliabilityPenalty, rp.created_at AS createdAt,
                   t.id AS teamId, t.name AS teamName, t.avatar_url AS teamAvatarUrl,
                   u.id AS mercenaryId, u.display_name AS mercenaryName, u.avatar_url AS mercenaryAvatarUrl,
                   (u.deleted_at IS NOT NULL) AS mercenaryDeleted,
                   d.id AS disputeId, d.status AS disputeStatus, d.evidence AS disputeEvidence,
                   d.decision AS disputeDecision, d.resolution_note AS disputeResolutionNote,
                   d.created_at AS disputeCreatedAt, d.resolved_at AS disputeResolvedAt
            FROM report rp
            JOIN mercenary_application a ON a.id = rp.application_id
            JOIN mercenary_recruitment rc ON rc.id = a.recruitment_id
            LEFT JOIN match m ON m.id = rc.match_id
            LEFT JOIN external_match x ON x.id = rc.external_match_id
            JOIN team t ON t.id = rc.team_id
            JOIN "user" u ON u.id = a.applicant_id
            LEFT JOIN dispute d ON d.report_id = rp.id
            """;

    String PAGE_SUFFIX = " ORDER BY rp.created_at DESC, rp.id DESC LIMIT :limit OFFSET :offset";

    /**
     * Context report theo đơn, xuất phát từ người gọi (luôn một dòng): đơn, trận, đội tuyển, cờ người gọi (đội trưởng
     * ACTIVE / ứng viên / người đã giải tán đội), đã có tin nhắn, đã report loại {@code reportType} theo từng phía.
     */
    @Query(value = "SELECT (me.deleted_at IS NOT NULL) AS callerDeleted, a.id AS applicationId,"
            + " CASE WHEN a.id IS NULL THEN NULL ELSE " + MercenarySql.APPLICATION_EFFECTIVE_STATUS
            + " END AS applicationStatus, a.applicant_id AS applicantId, ap.display_name AS applicantName,"
            + " ap.avatar_url AS applicantAvatarUrl, (ap.deleted_at IS NOT NULL) AS applicantDeleted,"
            + " r.match_id AS matchId, r.external_match_id AS externalMatchId, " + MercenarySql.GAME_ENDED
            + " AS gameEnded, " + MercenarySql.GAME_START + " AS gameStart," + """

                   t.id AS teamId, t.status AS teamStatus, t.name AS teamName, t.avatar_url AS teamAvatarUrl,
                   COALESCE(cm.user_id = :callerId, false) AS callerCaptain,
                   COALESCE(a.applicant_id = :callerId, false) AS callerApplicant,
                   COALESCE(t.disbanded_by = :callerId, false) AS callerDisbandedBy,
                   EXISTS (SELECT 1 FROM conversation c JOIN conversation_message cmsg ON cmsg.conversation_id = c.id
                           WHERE c.application_id = a.id) AS hasMessages,
                   EXISTS (SELECT 1 FROM report rp WHERE rp.application_id IS NOT NULL
                           AND (rp.match_id = r.match_id OR rp.external_match_id = r.external_match_id)
                           AND rp.reporter_type = 'TEAM' AND rp.reporter_id = t.id
                           AND rp.reported_type = 'INDIVIDUAL' AND rp.reported_id = a.applicant_id
                           AND rp.report_type = :reportType) AS teamReported,
                   EXISTS (SELECT 1 FROM report rp WHERE rp.application_id IS NOT NULL
                           AND (rp.match_id = r.match_id OR rp.external_match_id = r.external_match_id)
                           AND rp.reporter_type = 'INDIVIDUAL' AND rp.reporter_id = a.applicant_id
                           AND rp.reported_type = 'TEAM' AND rp.reported_id = t.id
                           AND rp.report_type = :reportType) AS applicantReported
            FROM "user" me
            LEFT JOIN mercenary_application a ON a.id = :applicationId
            LEFT JOIN "user" ap ON ap.id = a.applicant_id
            LEFT JOIN mercenary_recruitment r ON r.id = a.recruitment_id
            LEFT JOIN match m ON m.id = r.match_id
            LEFT JOIN external_match x ON x.id = r.external_match_id
            LEFT JOIN team t ON t.id = r.team_id
            LEFT JOIN team_member cm ON cm.team_id = t.id AND cm.role = 'CAPTAIN' AND cm.status = 'ACTIVE'
            WHERE me.id = :callerId
            """, nativeQuery = true)
    Optional<MercenaryReportContextView> findMercenaryReportContext(@Param("applicationId") UUID applicationId,
                                                                    @Param("callerId") UUID callerId,
                                                                    @Param("reportType") String reportType,
                                                                    @Param("now") LocalDateTime now);

    /** Report liên quan mercenary đội nhận (mercenary report đội). */
    @Query(value = MERCENARY_REPORT_SELECT + """
             WHERE rp.reported_type = 'TEAM' AND rp.reported_id = :subjectId AND rp.application_id IS NOT NULL
            """ + PAGE_SUFFIX, nativeQuery = true)
    List<MercenaryReportView> findTeamReceivedMercenaryReports(@Param("subjectId") UUID subjectId,
                                                               @Param("limit") int limit, @Param("offset") long offset);

    /** Report liên quan mercenary đội đã gửi (đội report mercenary). */
    @Query(value = MERCENARY_REPORT_SELECT + """
             WHERE rp.reporter_type = 'TEAM' AND rp.reporter_id = :subjectId AND rp.application_id IS NOT NULL
            """ + PAGE_SUFFIX, nativeQuery = true)
    List<MercenaryReportView> findTeamSentMercenaryReports(@Param("subjectId") UUID subjectId,
                                                           @Param("limit") int limit, @Param("offset") long offset);

    /** Report cá nhân nhận với vai trò mercenary (đội trưởng report mình). */
    @Query(value = MERCENARY_REPORT_SELECT + """
             WHERE rp.reported_type = 'INDIVIDUAL' AND rp.reported_id = :subjectId AND rp.application_id IS NOT NULL
            """ + PAGE_SUFFIX, nativeQuery = true)
    List<MercenaryReportView> findUserReceivedMercenaryReports(@Param("subjectId") UUID subjectId,
                                                               @Param("limit") int limit, @Param("offset") long offset);

    /** Report cá nhân đã gửi cho đội với vai trò mercenary. */
    @Query(value = MERCENARY_REPORT_SELECT + """
             WHERE rp.reporter_type = 'INDIVIDUAL' AND rp.reporter_id = :subjectId AND rp.application_id IS NOT NULL
            """ + PAGE_SUFFIX, nativeQuery = true)
    List<MercenaryReportView> findUserSentMercenaryReports(@Param("subjectId") UUID subjectId,
                                                           @Param("limit") int limit, @Param("offset") long offset);

    @Query(value = """
            SELECT COUNT(*) FROM report rp
            WHERE rp.reported_type = 'TEAM' AND rp.reported_id = :subjectId AND rp.application_id IS NOT NULL
            """, nativeQuery = true)
    long countTeamReceivedMercenaryReports(@Param("subjectId") UUID subjectId);

    @Query(value = """
            SELECT COUNT(*) FROM report rp
            WHERE rp.reporter_type = 'TEAM' AND rp.reporter_id = :subjectId AND rp.application_id IS NOT NULL
            """, nativeQuery = true)
    long countTeamSentMercenaryReports(@Param("subjectId") UUID subjectId);

    @Query(value = """
            SELECT COUNT(*) FROM report rp
            WHERE rp.reported_type = 'INDIVIDUAL' AND rp.reported_id = :subjectId AND rp.application_id IS NOT NULL
            """, nativeQuery = true)
    long countUserReceivedMercenaryReports(@Param("subjectId") UUID subjectId);

    @Query(value = """
            SELECT COUNT(*) FROM report rp
            WHERE rp.reporter_type = 'INDIVIDUAL' AND rp.reporter_id = :subjectId AND rp.application_id IS NOT NULL
            """, nativeQuery = true)
    long countUserSentMercenaryReports(@Param("subjectId") UUID subjectId);

    /**
     * Report liên quan mercenary của đội ({@code subjectType = TEAM}) hoặc cá nhân ({@code INDIVIDUAL}) theo chiều
     * {@code RECEIVED} / {@code SENT} — chọn một trong bốn câu cố định (không nối chuỗi cột).
     */
    default List<MercenaryReportView> findMercenaryReports(String subjectType, UUID subjectId, String direction,
                                                           int limit, long offset) {
        boolean team = SUBJECT_TEAM.equals(subjectType);
        boolean received = DIRECTION_RECEIVED.equals(direction);
        if (team) {
            return received ? findTeamReceivedMercenaryReports(subjectId, limit, offset)
                    : findTeamSentMercenaryReports(subjectId, limit, offset);
        }
        return received ? findUserReceivedMercenaryReports(subjectId, limit, offset)
                : findUserSentMercenaryReports(subjectId, limit, offset);
    }

    /** Đếm tương ứng {@link #findMercenaryReports}. */
    default long countMercenaryReports(String subjectType, UUID subjectId, String direction) {
        boolean team = SUBJECT_TEAM.equals(subjectType);
        boolean received = DIRECTION_RECEIVED.equals(direction);
        if (team) {
            return received ? countTeamReceivedMercenaryReports(subjectId) : countTeamSentMercenaryReports(subjectId);
        }
        return received ? countUserReceivedMercenaryReports(subjectId) : countUserSentMercenaryReports(subjectId);
    }

    /**
     * Ghi quyết định Admin lên report đang tranh chấp (CAS {@code DISPUTED}); trả bên bị report / bên report, loại và
     * điểm đã thực trừ để hoàn đúng bảng (đội hoặc cá nhân).
     */
    @Query(value = """
            UPDATE report SET status = :decision, updated_at = :now
            WHERE id = :reportId AND status = 'DISPUTED'
            RETURNING reported_type AS reportedType, reported_id AS reportedId, reporter_type AS reporterType,
                      reporter_id AS reporterId, report_type AS reportType, reliability_penalty AS reliabilityPenalty
            """, nativeQuery = true)
    Optional<ReportDecisionView> applyDecision(@Param("reportId") UUID reportId, @Param("decision") String decision,
                                               @Param("now") LocalDateTime now);
}
