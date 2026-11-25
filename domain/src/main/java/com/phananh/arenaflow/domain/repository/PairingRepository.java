package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.entity.match.Pairing;
import com.phananh.arenaflow.domain.projection.ConfirmSideResult;
import com.phananh.arenaflow.domain.projection.PairingContextView;
import com.phananh.arenaflow.domain.projection.PairingDetailView;
import com.phananh.arenaflow.domain.projection.PairingSummaryView;
import com.phananh.arenaflow.domain.projection.ProposalCandidateView;
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
 * Thương lượng luồng B. Entity chỉ INSERT qua JPA; mọi đổi trạng thái là câu native có điều kiện ({@code status},
 * {@code revision}) trong chính câu ghi — 0 dòng nghĩa là dữ liệu vừa bị thao tác khác đổi. Thời gian {@code :now}
 * do Java truyền (giờ Việt Nam).
 */
public interface PairingRepository extends JpaRepository<Pairing, UUID> {

    /** Trạng thái hiển thị: thương lượng đang trao đổi trên kèo OPEN đã quá hạn bài đăng → EXPIRED. */
    String EFFECTIVE_STATUS = """
            CASE WHEN p.status = 'NEGOTIATING' AND m.status = 'OPEN' AND m.expires_at <= :now
                 THEN 'EXPIRED' ELSE p.status END""";

    /** Cuộc trò chuyện của thương lượng: mỗi (kèo, đội nhận kèo) một cuộc trò chuyện TEAM_MATCH. */
    String CONVERSATION_JOIN = """
            LEFT JOIN conversation cv ON cv.context_type = 'TEAM_MATCH' AND cv.match_id = p.match_id
                                     AND cv.party_b_id = p.interested_team_id""";

    String SUMMARY_COLUMNS = """
            SELECT p.id AS pairingId, p.match_id AS matchId, cv.id AS conversationId,
                   """ + EFFECTIVE_STATUS + """
             AS effectiveStatus, p.revision AS revision,
                   m.author_team_id AS authorTeamId, ta.name AS authorTeamName,
                   p.interested_team_id AS interestedTeamId, ti.name AS interestedTeamName,
                   ti.reliability_score AS interestedTeamReliabilityScore,
                   p.proposed_venue_id AS proposedVenueId, p.proposed_time_option_id AS proposedTimeOptionId,
                   p.author_confirmed_at AS authorConfirmedAt, p.interested_confirmed_at AS interestedConfirmedAt,
                   p.final_confirmed_at AS finalConfirmedAt, m.match_start_time AS matchStartTime,
                   m.expires_at AS matchExpiresAt, p.created_at AS createdAt, p.updated_at AS updatedAt
            FROM pairing p
            JOIN match m ON m.id = p.match_id
            JOIN team ta ON ta.id = m.author_team_id
            JOIN team ti ON ti.id = p.interested_team_id
            """ + CONVERSATION_JOIN + "\n";

    String STATUS_FILTER = "(CAST(:status AS VARCHAR) IS NULL OR " + EFFECTIVE_STATUS
            + " = CAST(:status AS VARCHAR))";

    String TEAM_ROLE_FILTER = """
            ((CAST(:role AS VARCHAR) IS NULL AND (p.interested_team_id = :teamId OR m.author_team_id = :teamId))
             OR (CAST(:role AS VARCHAR) = 'INTERESTED' AND p.interested_team_id = :teamId)
             OR (CAST(:role AS VARCHAR) = 'AUTHOR' AND m.author_team_id = :teamId))""";

    /**
     * Ngữ cảnh ghi (2.5–2.7) trong một câu: pairing + kèo ({@code version}) + trạng thái hai đội + bộ môn + khung giờ
     * và trạng thái địa điểm của phương án + cuộc trò chuyện + cờ người gọi là đội trưởng ACTIVE từng phía.
     */
    @Query(value = """
            SELECT p.id AS id, p.match_id AS matchId, p.interested_team_id AS interestedTeamId, p.status AS status,
                   p.revision AS revision, p.author_confirmed_at AS authorConfirmedAt,
                   p.interested_confirmed_at AS interestedConfirmedAt, p.proposed_venue_id AS proposedVenueId,
                   p.proposed_time_option_id AS proposedTimeOptionId, p.cost_type AS costType,
                   p.cost_split AS costSplit, p.cost_venue_amount AS costVenueAmount,
                   p.cost_drinks_amount AS costDrinksAmount, p.cost_deposit_amount AS costDepositAmount,
                   p.cost_deposit_holder AS costDepositHolder, p.updated_at AS updatedAt,
                   m.author_team_id AS authorTeamId, m.status AS matchStatus, m.expires_at AS expiresAt,
                   m.type AS matchType, m.version AS matchVersion,
                   ta.status AS authorTeamStatus, ti.status AS interestedTeamStatus, s.is_active AS sportActive,
                   mto.proposed_start_time AS proposedStartTime, mto.proposed_end_time AS proposedEndTime,
                   pv.status AS proposedVenueStatus, cv.id AS conversationId,
                   EXISTS (SELECT 1 FROM team_member ca WHERE ca.team_id = ta.id AND ca.user_id = :callerId
                           AND ca.role = 'CAPTAIN' AND ca.status = 'ACTIVE') AS callerAuthorCaptain,
                   EXISTS (SELECT 1 FROM team_member ci WHERE ci.team_id = ti.id AND ci.user_id = :callerId
                           AND ci.role = 'CAPTAIN' AND ci.status = 'ACTIVE') AS callerInterestedCaptain
            FROM pairing p
            JOIN match m ON m.id = p.match_id
            JOIN team ta ON ta.id = m.author_team_id
            JOIN team ti ON ti.id = p.interested_team_id
            JOIN sport s ON s.id = m.sport_id
            LEFT JOIN match_time_option mto ON mto.id = p.proposed_time_option_id
            LEFT JOIN venue pv ON pv.id = p.proposed_venue_id
            """ + CONVERSATION_JOIN + """

            WHERE p.id = :pairingId
            """, nativeQuery = true)
    Optional<PairingContextView> findContext(@Param("pairingId") UUID pairingId, @Param("callerId") UUID callerId);

    /**
     * Chi tiết (2.4) trong một câu. Đội trưởng hiện tại mỗi đội qua LEFT JOIN (đội đã giải tán → {@code null});
     * {@code COALESCE} cho cờ quyền vì so sánh với {@code NULL} ra {@code NULL}.
     */
    @Query(value = """
            SELECT p.id AS pairingId, p.match_id AS matchId, cv.id AS conversationId,
                   """ + EFFECTIVE_STATUS + """
             AS effectiveStatus, p.revision AS revision, m.expires_at AS matchExpiresAt,
                   ta.id AS authorTeamId, ta.name AS authorTeamName, ta.avatar_url AS authorTeamAvatarUrl,
                   ta.reliability_score AS authorTeamReliabilityScore, ua.display_name AS authorCaptainName,
                   (ua.deleted_at IS NOT NULL) AS authorCaptainDeleted,
                   ti.id AS interestedTeamId, ti.name AS interestedTeamName, ti.avatar_url AS interestedTeamAvatarUrl,
                   ti.reliability_score AS interestedTeamReliabilityScore, ui.display_name AS interestedCaptainName,
                   (ui.deleted_at IS NOT NULL) AS interestedCaptainDeleted,
                   p.proposed_venue_id AS proposedVenueId, pv.name AS proposedVenueName,
                   pv.address AS proposedVenueAddress, pv.status AS proposedVenueStatus,
                   p.proposed_time_option_id AS proposedTimeOptionId, mto.proposed_start_time AS proposedStartTime,
                   mto.proposed_end_time AS proposedEndTime,
                   p.author_confirmed_at AS authorConfirmedAt, p.interested_confirmed_at AS interestedConfirmedAt,
                   p.final_confirmed_at AS finalConfirmedAt, p.cost_type AS costType, p.cost_split AS costSplit,
                   p.cost_venue_amount AS costVenueAmount, p.cost_drinks_amount AS costDrinksAmount,
                   p.cost_deposit_amount AS costDepositAmount, p.cost_deposit_holder AS costDepositHolder,
                   p.created_at AS createdAt, p.updated_at AS updatedAt,
                   COALESCE(ca.user_id = :callerId, false) AS callerAuthorCaptain,
                   COALESCE(ci.user_id = :callerId, false) AS callerInterestedCaptain
            FROM pairing p
            JOIN match m ON m.id = p.match_id
            JOIN team ta ON ta.id = m.author_team_id
            JOIN team ti ON ti.id = p.interested_team_id
            LEFT JOIN team_member ca ON ca.team_id = ta.id AND ca.role = 'CAPTAIN' AND ca.status = 'ACTIVE'
            LEFT JOIN "user" ua ON ua.id = ca.user_id
            LEFT JOIN team_member ci ON ci.team_id = ti.id AND ci.role = 'CAPTAIN' AND ci.status = 'ACTIVE'
            LEFT JOIN "user" ui ON ui.id = ci.user_id
            LEFT JOIN match_time_option mto ON mto.id = p.proposed_time_option_id
            LEFT JOIN venue pv ON pv.id = p.proposed_venue_id
            """ + CONVERSATION_JOIN + """

            WHERE p.id = :pairingId
            """, nativeQuery = true)
    Optional<PairingDetailView> findDetailById(@Param("pairingId") UUID pairingId, @Param("callerId") UUID callerId,
                                               @Param("now") LocalDateTime now);

    /** Thương lượng của một kèo (2.2); {@code status} null = tất cả. Thứ tự: created_at, id tăng dần. */
    @Query(value = SUMMARY_COLUMNS + "WHERE p.match_id = :matchId AND " + STATUS_FILTER
            + "\nORDER BY p.created_at, p.id",
            countQuery = "SELECT COUNT(*) FROM pairing p JOIN match m ON m.id = p.match_id WHERE p.match_id = :matchId AND "
                    + STATUS_FILTER,
            nativeQuery = true)
    Page<PairingSummaryView> findByMatch(@Param("matchId") UUID matchId, @Param("status") String status,
                                         @Param("now") LocalDateTime now, Pageable pageable);

    /**
     * Thương lượng của đội (2.3): {@code role} INTERESTED = đội nhận kèo, AUTHOR = kèo của đội, null = cả hai;
     * {@code status} null = tất cả. Thứ tự: updated_at, id giảm dần.
     */
    @Query(value = SUMMARY_COLUMNS + "WHERE " + TEAM_ROLE_FILTER + " AND " + STATUS_FILTER
            + "\nORDER BY p.updated_at DESC, p.id DESC",
            countQuery = "SELECT COUNT(*) FROM pairing p JOIN match m ON m.id = p.match_id WHERE " + TEAM_ROLE_FILTER
                    + " AND " + STATUS_FILTER,
            nativeQuery = true)
    Page<PairingSummaryView> findByTeam(@Param("teamId") UUID teamId, @Param("role") String role,
                                        @Param("status") String status, @Param("now") LocalDateTime now,
                                        Pageable pageable);

    /** Cặp (địa điểm, khung giờ) thuộc lựa chọn của chính kèo; không có dòng = không thuộc kèo. */
    @Query(value = """
            SELECT v.id AS venueId, v.name AS venueName, v.address AS address, v.status AS venueStatus,
                   t.id AS timeOptionId, t.proposed_start_time AS startsAt, t.proposed_end_time AS endsAt
            FROM match_venue_option o
            JOIN venue v ON v.id = o.venue_id
            JOIN match_time_option t ON t.match_id = o.match_id AND t.id = :timeOptionId
            WHERE o.match_id = :matchId AND o.venue_id = :venueId
            """, nativeQuery = true)
    Optional<ProposalCandidateView> findProposalCandidate(@Param("matchId") UUID matchId,
                                                          @Param("venueId") UUID venueId,
                                                          @Param("timeOptionId") UUID timeOptionId);

    /** Đổi phương án (CAS {@code NEGOTIATING} + {@code revision}); xoá xác nhận của cả hai bên, {@code revision + 1}. */
    @Modifying
    @Query(value = """
            UPDATE pairing SET proposed_venue_id = :venueId, proposed_time_option_id = :timeOptionId,
                   author_confirmed_at = NULL, interested_confirmed_at = NULL, revision = revision + 1,
                   updated_at = :now
            WHERE id = :id AND status = 'NEGOTIATING' AND revision = :expectedRevision
            """, nativeQuery = true)
    int updateProposal(@Param("id") UUID id, @Param("venueId") UUID venueId, @Param("timeOptionId") UUID timeOptionId,
                       @Param("expectedRevision") int expectedRevision, @Param("now") LocalDateTime now);

    /** Thay thoả thuận chi phí + cọc (CAS như {@link #updateProposal}); xoá xác nhận, {@code revision + 1}. */
    @Modifying
    @Query(value = """
            UPDATE pairing SET cost_type = :costType, cost_split = :costSplit, cost_venue_amount = :venueAmount,
                   cost_drinks_amount = :drinksAmount, cost_deposit_amount = :depositAmount,
                   cost_deposit_holder = :depositHolder, author_confirmed_at = NULL, interested_confirmed_at = NULL,
                   revision = revision + 1, updated_at = :now
            WHERE id = :id AND status = 'NEGOTIATING' AND revision = :expectedRevision
            """, nativeQuery = true)
    int updateCost(@Param("id") UUID id, @Param("costType") String costType, @Param("costSplit") String costSplit,
                   @Param("venueAmount") Long venueAmount, @Param("drinksAmount") Long drinksAmount,
                   @Param("depositAmount") Long depositAmount, @Param("depositHolder") String depositHolder,
                   @Param("expectedRevision") int expectedRevision, @Param("now") LocalDateTime now);

    /**
     * Ghi xác nhận của một phía ({@code side} = AUTHOR | INTERESTED — chọn cột bằng CASE, không ghép tên cột từ
     * input) và trả hai mốc xác nhận sau khi ghi. Khai báo không {@code @Modifying}: câu trả dòng nên chạy như query.
     * Rỗng = không còn NEGOTIATING / revision lệch / phía này đã xác nhận.
     */
    @Query(value = """
            UPDATE pairing
            SET author_confirmed_at = CASE WHEN CAST(:side AS VARCHAR) = 'AUTHOR' THEN :now ELSE author_confirmed_at END,
                interested_confirmed_at = CASE WHEN CAST(:side AS VARCHAR) = 'INTERESTED' THEN :now
                                               ELSE interested_confirmed_at END,
                updated_at = :now
            WHERE id = :id AND status = 'NEGOTIATING' AND revision = :expectedRevision
              AND ((CAST(:side AS VARCHAR) = 'AUTHOR' AND author_confirmed_at IS NULL)
                   OR (CAST(:side AS VARCHAR) = 'INTERESTED' AND interested_confirmed_at IS NULL))
            RETURNING author_confirmed_at AS authorConfirmedAt, interested_confirmed_at AS interestedConfirmedAt
            """, nativeQuery = true)
    Optional<ConfirmSideResult> confirmSide(@Param("id") UUID id, @Param("side") String side,
                                            @Param("expectedRevision") int expectedRevision,
                                            @Param("now") LocalDateTime now);

    /**
     * Hoàn tất: pairing được chọn → CONFIRMED (điền mốc xác nhận còn thiếu + {@code final_confirmed_at}), mọi pairing
     * NEGOTIATING khác của kèo → SUPERSEDED; một câu. Trả id các dòng đã đổi — không chứa {@code pairingId} nghĩa là
     * gói vừa bị sửa (revision lệch). Chạy sau CAS trên {@code match} (khoá kèo trước pairing).
     */
    @Query(value = """
            UPDATE pairing
            SET status = CASE WHEN id = :pairingId THEN 'CONFIRMED' ELSE 'SUPERSEDED' END,
                author_confirmed_at = CASE WHEN id = :pairingId THEN COALESCE(author_confirmed_at, :now)
                                           ELSE author_confirmed_at END,
                interested_confirmed_at = CASE WHEN id = :pairingId THEN COALESCE(interested_confirmed_at, :now)
                                               ELSE interested_confirmed_at END,
                final_confirmed_at = CASE WHEN id = :pairingId THEN CAST(:now AS timestamp) END,
                updated_at = :now
            WHERE match_id = :matchId AND status = 'NEGOTIATING' AND (id <> :pairingId OR revision = :expectedRevision)
            RETURNING id
            """, nativeQuery = true)
    List<UUID> finalizeConfirmation(@Param("matchId") UUID matchId, @Param("pairingId") UUID pairingId,
                                    @Param("expectedRevision") int expectedRevision, @Param("now") LocalDateTime now);

    /** Huỷ kèo khi còn OPEN: mọi thương lượng đang trao đổi → SUPERSEDED. */
    @Modifying
    @Query(value = """
            UPDATE pairing SET status = 'SUPERSEDED', updated_at = :now
            WHERE match_id = :matchId AND status = 'NEGOTIATING'
            """, nativeQuery = true)
    int supersedeNegotiating(@Param("matchId") UUID matchId, @Param("now") LocalDateTime now);

    /** Kèo hết hạn: thương lượng đang trao đổi của các kèo → EXPIRED. */
    @Modifying
    @Query(value = """
            UPDATE pairing SET status = 'EXPIRED', updated_at = :now
            WHERE match_id IN (:matchIds) AND status = 'NEGOTIATING'
            """, nativeQuery = true)
    int expireNegotiatingByMatchIds(@Param("matchIds") Collection<UUID> matchIds, @Param("now") LocalDateTime now);
}
