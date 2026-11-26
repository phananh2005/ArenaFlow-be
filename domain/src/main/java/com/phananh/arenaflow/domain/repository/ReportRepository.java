package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.entity.rating.Report;
import com.phananh.arenaflow.domain.projection.ChatReportContextView;
import com.phananh.arenaflow.domain.projection.DisputeContextView;
import com.phananh.arenaflow.domain.projection.ReportDecisionView;
import com.phananh.arenaflow.domain.projection.TeamReportView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReportRepository extends JpaRepository<Report, UUID> {

    /** Cột chung của danh sách report đội ↔ đội; {@code ct} = đội còn lại, {@code d} = tranh chấp (LEFT JOIN). */
    String TEAM_REPORT_COLUMNS = """
            SELECT r.id AS reportId, r.match_id AS matchId, m.match_start_time AS matchStartTime,
                   ct.id AS counterpartTeamId, ct.name AS counterpartTeamName, ct.avatar_url AS counterpartTeamAvatarUrl,
                   r.report_type AS reportType, r.reason AS reason, r.status AS status,
                   r.reliability_penalty AS reliabilityPenalty, r.created_at AS createdAt,
                   d.id AS disputeId, d.status AS disputeStatus, d.evidence AS disputeEvidence,
                   d.decision AS disputeDecision, d.resolution_note AS disputeResolutionNote,
                   d.created_at AS disputeCreatedAt, d.resolved_at AS disputeResolvedAt
            """;

    /** Report đội bị report (đội còn lại = đội report); mới nhất trước (index theo đội bị report + thời điểm). */
    @Query(value = TEAM_REPORT_COLUMNS + """
            FROM report r
            JOIN match m ON m.id = r.match_id
            JOIN team ct ON ct.id = r.reporter_id
            LEFT JOIN dispute d ON d.report_id = r.id
            WHERE r.reported_type = 'TEAM' AND r.reported_id = :teamId AND r.reporter_type = 'TEAM'
            ORDER BY r.created_at DESC, r.id DESC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<TeamReportView> findReceivedReports(@Param("teamId") UUID teamId, @Param("limit") int limit,
                                             @Param("offset") long offset);

    @Query(value = """
            SELECT COUNT(*) FROM report r
            WHERE r.reported_type = 'TEAM' AND r.reported_id = :teamId AND r.reporter_type = 'TEAM'
            """, nativeQuery = true)
    long countReceivedReports(@Param("teamId") UUID teamId);

    /** Report đội đã gửi (đội còn lại = đội bị report); mới nhất trước (index theo đội report + thời điểm). */
    @Query(value = TEAM_REPORT_COLUMNS + """
            FROM report r
            JOIN match m ON m.id = r.match_id
            JOIN team ct ON ct.id = r.reported_id
            LEFT JOIN dispute d ON d.report_id = r.id
            WHERE r.reporter_type = 'TEAM' AND r.reporter_id = :teamId AND r.reported_type = 'TEAM'
            ORDER BY r.created_at DESC, r.id DESC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<TeamReportView> findSentReports(@Param("teamId") UUID teamId, @Param("limit") int limit,
                                         @Param("offset") long offset);

    @Query(value = """
            SELECT COUNT(*) FROM report r
            WHERE r.reporter_type = 'TEAM' AND r.reporter_id = :teamId AND r.reported_type = 'TEAM'
            """, nativeQuery = true)
    long countSentReports(@Param("teamId") UUID teamId);

    /**
     * Ngữ cảnh tranh chấp trong một câu: report, trạng thái đội bị report, người gọi là đội trưởng ACTIVE của đội bị
     * report, đã có tranh chấp. Rỗng = không có report.
     */
    @Query(value = """
            SELECT r.id AS reportId, r.report_type AS reportType, r.status AS status, r.created_at AS createdAt,
                   r.reporter_id AS reporterTeamId, r.reported_id AS reportedTeamId, t.status AS reportedTeamStatus,
                   EXISTS (SELECT 1 FROM team_member cm WHERE cm.team_id = r.reported_id AND cm.user_id = :callerId
                           AND cm.role = 'CAPTAIN' AND cm.status = 'ACTIVE') AS callerReportedCaptain,
                   EXISTS (SELECT 1 FROM dispute d WHERE d.report_id = r.id) AS disputeExists
            FROM report r
            LEFT JOIN team t ON r.reported_type = 'TEAM' AND t.id = r.reported_id
            WHERE r.id = :reportId
            """, nativeQuery = true)
    Optional<DisputeContextView> findDisputeContext(@Param("reportId") UUID reportId, @Param("callerId") UUID callerId);

    /**
     * AUTO_ACCEPTED → DISPUTED có điều kiện: chỉ report bùng còn trong hạn ({@code created_at > createdAfter}, hạn
     * kiểm trong chính câu này). 0 dòng = trạng thái đã đổi hoặc vừa quá hạn.
     */
    @Modifying
    @Query(value = """
            UPDATE report SET status = 'DISPUTED', updated_at = :now
            WHERE id = :id AND status = 'AUTO_ACCEPTED' AND report_type = 'NOSHOW' AND created_at > :createdAfter
            """, nativeQuery = true)
    int markDisputed(@Param("id") UUID reportId, @Param("now") LocalDateTime now,
                     @Param("createdAfter") LocalDateTime createdAfter);

    /**
     * DISPUTED → quyết định của Admin có điều kiện, trả hai đội, loại và điểm đã thực trừ. Không {@code @Modifying} vì
     * câu trả về dòng.
     */
    @Query(value = """
            UPDATE report SET status = CAST(:decision AS varchar), updated_at = :now
            WHERE id = :id AND status = 'DISPUTED'
            RETURNING reported_id AS reportedTeamId, reporter_id AS reporterTeamId, report_type AS reportType,
                      reliability_penalty AS reliabilityPenalty
            """, nativeQuery = true)
    Optional<ReportDecisionView> applyDecision(@Param("id") UUID reportId, @Param("decision") String decision,
                                               @Param("now") LocalDateTime now);

    /**
     * Ngữ cảnh report "giao tiếp tệ" qua chat trong một câu: cột ngữ cảnh chung của cuộc trò chuyện + giờ trận + đã có
     * tin nhắn + mỗi chiều bên A ↔ bên B đã có report giao tiếp tệ trong kèo (tiền tố index chống trùng report).
     * Giờ trận lấy từ kèo của khối join chung (chỉ có với chat hai đội — loại khác bị từ chối trước khi dùng);
     * {@code now} dùng cho trạng thái hiển thị yêu cầu / thương lượng của khối cột chung.
     */
    @Query(value = "SELECT " + ConversationSql.CONTEXT_COLUMNS + """
            , m.match_start_time AS matchStartTime,
              EXISTS (SELECT 1 FROM conversation_message cm WHERE cm.conversation_id = c.id) AS hasMessages,
              EXISTS (SELECT 1 FROM report r WHERE r.match_id = c.match_id
                      AND r.reporter_type = 'TEAM' AND r.reporter_id = c.party_a_id
                      AND r.reported_type = 'TEAM' AND r.reported_id = c.party_b_id
                      AND r.report_type = 'BAD_COMMUNICATION') AS aReportedB,
              EXISTS (SELECT 1 FROM report r WHERE r.match_id = c.match_id
                      AND r.reporter_type = 'TEAM' AND r.reporter_id = c.party_b_id
                      AND r.reported_type = 'TEAM' AND r.reported_id = c.party_a_id
                      AND r.report_type = 'BAD_COMMUNICATION') AS bReportedA
            FROM conversation c
            """ + ConversationSql.CONTEXT_JOINS + """
            WHERE c.id = :conversationId
            """, nativeQuery = true)
    Optional<ChatReportContextView> findChatReportContext(@Param("conversationId") UUID conversationId,
                                                          @Param("callerId") UUID callerId,
                                                          @Param("now") LocalDateTime now);
}
