package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.entity.match.MatchRequest;
import com.phananh.arenaflow.domain.projection.MatchRequestContextView;
import com.phananh.arenaflow.domain.projection.MatchRequestView;
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
 * Yêu cầu ghép kèo. Không có version: mọi chuyển trạng thái là {@code UPDATE … WHERE status = '<nguồn>'} — 0 dòng
 * nghĩa là thao tác khác đã đổi trạng thái (READ COMMITTED đánh giá lại sau khi chờ khoá dòng).
 */
public interface MatchRequestRepository extends JpaRepository<MatchRequest, UUID> {

    /** Trạng thái hiển thị: yêu cầu PENDING của kèo OPEN đã tới hạn → EXPIRED. */
    String EFFECTIVE_STATUS = """
            CASE WHEN r.status = 'PENDING' AND m.status = 'OPEN' AND m.expires_at <= :now THEN 'EXPIRED'
                 ELSE r.status END""";

    /** Cuộc trò chuyện của cặp (kèo, đội gửi) — dùng tiếp khi đội gửi lại. */
    String CONVERSATION_JOIN = """
            LEFT JOIN conversation cv ON cv.context_type = 'TEAM_MATCH' AND cv.match_id = r.match_id
                 AND cv.party_b_id = r.requester_team_id
            """;

    String REQUEST_COLUMNS = """
            r.id AS requestId, r.match_id AS matchId, cv.id AS conversationId, r.requester_team_id AS requesterTeamId,
            rt.name AS requesterTeamName, rt.reliability_score AS requesterTeamReliabilityScore, r.reason AS reason,
            r.requested_at AS requestedAt, r.responded_at AS respondedAt, r.withdrawn_at AS withdrawnAt,
            """;

    boolean existsByMatchIdAndStatus(UUID matchId, MatchRequest.Status status);

    @Query(value = """
            SELECT r.id AS requestId, r.match_id AS matchId, r.requester_team_id AS requesterTeamId, r.status AS status,
                   m.author_team_id AS authorTeamId, m.status AS matchStatus, m.type AS matchType,
                   m.expires_at AS expiresAt, m.match_start_time AS matchStartTime,
                   rt.name AS requesterTeamName, rt.status AS requesterTeamStatus,
                   rt.reliability_score AS requesterTeamReliabilityScore, s.is_active AS sportActive
            FROM match_request r
            JOIN match m ON m.id = r.match_id
            JOIN team rt ON rt.id = r.requester_team_id
            JOIN sport s ON s.id = m.sport_id
            WHERE r.id = :requestId
            """, nativeQuery = true)
    Optional<MatchRequestContextView> findContextById(@Param("requestId") UUID requestId);

    /** Yêu cầu gửi tới một kèo (gửi trước lên trước); {@code pendingOnly} lọc trên trạng thái hiển thị. */
    @Query(value = "SELECT " + REQUEST_COLUMNS + EFFECTIVE_STATUS + """
             AS effectiveStatus,
            CAST(NULL AS timestamp) AS startsAt, CAST(NULL AS uuid) AS authorTeamId, CAST(NULL AS varchar) AS authorTeamName
            FROM match_request r
            JOIN match m ON m.id = r.match_id
            JOIN team rt ON rt.id = r.requester_team_id
            """ + CONVERSATION_JOIN + """
            WHERE r.match_id = :matchId AND (:pendingOnly = FALSE OR
            """ + EFFECTIVE_STATUS + " = 'PENDING') ORDER BY r.requested_at ASC, r.id ASC",
            countQuery = "SELECT COUNT(*) FROM match_request r JOIN match m ON m.id = r.match_id "
                    + "WHERE r.match_id = :matchId AND (:pendingOnly = FALSE OR " + EFFECTIVE_STATUS + " = 'PENDING')",
            nativeQuery = true)
    Page<MatchRequestView> findByMatch(@Param("matchId") UUID matchId, @Param("pendingOnly") boolean pendingOnly,
                                       @Param("now") LocalDateTime now, Pageable pageable);

    /** Yêu cầu một đội đã gửi (mới gửi lên trước) kèm ngữ cảnh kèo; {@code status} null = tất cả. */
    @Query(value = "SELECT " + REQUEST_COLUMNS + EFFECTIVE_STATUS + """
             AS effectiveStatus,
            m.match_start_time AS startsAt, m.author_team_id AS authorTeamId, aut.name AS authorTeamName
            FROM match_request r
            JOIN match m ON m.id = r.match_id
            JOIN team rt ON rt.id = r.requester_team_id
            JOIN team aut ON aut.id = m.author_team_id
            """ + CONVERSATION_JOIN + """
            WHERE r.requester_team_id = :teamId AND (CAST(:status AS varchar) IS NULL OR
            """ + EFFECTIVE_STATUS + " = CAST(:status AS varchar)) ORDER BY r.requested_at DESC, r.id DESC",
            countQuery = "SELECT COUNT(*) FROM match_request r JOIN match m ON m.id = r.match_id "
                    + "WHERE r.requester_team_id = :teamId AND (CAST(:status AS varchar) IS NULL OR "
                    + EFFECTIVE_STATUS + " = CAST(:status AS varchar))",
            nativeQuery = true)
    Page<MatchRequestView> findByRequesterTeam(@Param("teamId") UUID teamId, @Param("status") String status,
                                               @Param("now") LocalDateTime now, Pageable pageable);

    @Modifying
    @Query(value = "UPDATE match_request SET status = 'ACCEPTED', responded_at = :now WHERE id = :id AND status = 'PENDING'",
            nativeQuery = true)
    int acceptIfPending(@Param("id") UUID id, @Param("now") LocalDateTime now);

    @Modifying
    @Query(value = """
            UPDATE match_request SET status = 'REJECTED', responded_at = :now, reason = :reason
            WHERE id = :id AND status = 'PENDING'
            """, nativeQuery = true)
    int rejectIfPending(@Param("id") UUID id, @Param("reason") String reason, @Param("now") LocalDateTime now);

    @Modifying
    @Query(value = """
            UPDATE match_request SET status = 'WITHDRAWN', withdrawn_at = :now, reason = :reason
            WHERE id = :id AND status = 'PENDING'
            """, nativeQuery = true)
    int withdrawIfPending(@Param("id") UUID id, @Param("reason") String reason, @Param("now") LocalDateTime now);

    /** Chạy SAU khi chấp nhận một yêu cầu (yêu cầu thắng đã rời PENDING): các yêu cầu còn chờ → SUPERSEDED. */
    @Modifying
    @Query(value = """
            UPDATE match_request SET status = 'SUPERSEDED', responded_at = :now
            WHERE match_id = :matchId AND status = 'PENDING'
            """, nativeQuery = true)
    int supersedePending(@Param("matchId") UUID matchId, @Param("now") LocalDateTime now);

    /** Yêu cầu còn PENDING của các kèo vừa hết hạn → EXPIRED. */
    @Modifying
    @Query(value = "UPDATE match_request SET status = 'EXPIRED' WHERE match_id IN (:matchIds) AND status = 'PENDING'",
            nativeQuery = true)
    int expirePendingByMatchIds(@Param("matchIds") Collection<UUID> matchIds);

    /**
     * Đội đã ghép rút yêu cầu ACCEPTED (= huỷ kèo đã ghép); trả id yêu cầu đã rút để event mang theo mà không đọc
     * thêm. Câu trả dòng nên khai báo không có {@code @Modifying}; đúng 1 dòng là bất biến.
     */
    @Query(value = """
            UPDATE match_request SET status = 'WITHDRAWN', withdrawn_at = :now, reason = :reason
            WHERE match_id = :matchId AND requester_team_id = :requesterTeamId AND status = 'ACCEPTED'
            RETURNING id
            """, nativeQuery = true)
    List<UUID> withdrawAccepted(@Param("matchId") UUID matchId, @Param("requesterTeamId") UUID requesterTeamId,
                                @Param("reason") String reason, @Param("now") LocalDateTime now);
}
