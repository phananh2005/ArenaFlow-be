package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.entity.team.TeamMember;
import com.phananh.arenaflow.domain.projection.MemberHistoryView;
import com.phananh.arenaflow.domain.projection.MemberView;
import com.phananh.arenaflow.domain.projection.MyInvitationView;
import com.phananh.arenaflow.domain.projection.MyTeamView;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Truy cập bảng {@code team_member}: thành viên/lời mời của đội, lịch sử thành viên, đóng membership hàng loạt. */
public interface TeamMemberRepository extends JpaRepository<TeamMember, UUID> {

    Optional<TeamMember> findByTeamIdAndUserIdAndStatus(UUID teamId, UUID userId, TeamMember.Status status);

    Optional<TeamMember> findByTeamIdAndRoleAndStatus(UUID teamId, TeamMember.Role role, TeamMember.Status status);

    boolean existsByTeamIdAndUserIdAndStatusIn(UUID teamId, UUID userId, Collection<TeamMember.Status> statuses);

    /**
     * Thành viên ACTIVE kèm cờ user đã xoá để mapper che tên/avatar. Sort cố định: captain trước, joined_at, user_id.
     */
    @Query(value = """
            SELECT m.id AS memberId, m.team_id AS teamId, m.user_id AS userId,
                   u.display_name AS displayName, u.avatar_url AS avatarUrl, (u.deleted_at IS NOT NULL) AS userDeleted,
                   m.role AS role, m.status AS status, m.joined_at AS joinedAt
            FROM team_member m
            JOIN "user" u ON u.id = m.user_id
            WHERE m.team_id = :teamId AND m.status = 'ACTIVE'
            ORDER BY CASE WHEN m.role = 'CAPTAIN' THEN 0 ELSE 1 END, m.joined_at ASC, m.user_id ASC
            """,
            countQuery = "SELECT COUNT(*) FROM team_member m WHERE m.team_id = :teamId AND m.status = 'ACTIVE'",
            nativeQuery = true)
    Page<MemberView> findActiveMembers(@Param("teamId") UUID teamId, Pageable pageable);

    /**
     * Lịch sử thành viên cho Admin, không lọc theo trạng thái đội: {@code statuses} ⊆ {ACTIVE, LEFT} (caller không
     * truyền PENDING/REJECTED). Kèm cờ user đã xoá để mapper che tên/avatar. Sort cố định: joined_at tăng dần, id.
     */
    @Query(value = """
            SELECT m.id AS memberId, m.team_id AS teamId, m.user_id AS userId,
                   u.display_name AS displayName, u.avatar_url AS avatarUrl, (u.deleted_at IS NOT NULL) AS userDeleted,
                   m.role AS role, m.status AS status, m.joined_at AS joinedAt, m.left_at AS leftAt
            FROM team_member m
            JOIN "user" u ON u.id = m.user_id
            WHERE m.team_id = :teamId AND m.status IN (:statuses)
            ORDER BY m.joined_at ASC, m.id ASC
            """,
            countQuery = "SELECT COUNT(*) FROM team_member m WHERE m.team_id = :teamId AND m.status IN (:statuses)",
            nativeQuery = true)
    Page<MemberHistoryView> findMemberHistory(@Param("teamId") UUID teamId,
                                              @Param("statuses") Collection<String> statuses,
                                              Pageable pageable);

    /**
     * Giải tán đội: một câu cho mọi row còn lại — ACTIVE → LEFT, PENDING → REJECTED (không set left_at).
     * {@code left_at = GREATEST(:now, COALESCE(joined_at, invited_at))} giữ CHECK left_at ≥ joined_at khi accept commit
     * sau :now. Bulk update → tự tăng version. Gọi sau khi captain và team đã flush.
     */
    @Modifying
    @Query(value = """
            UPDATE team_member
            SET status = CASE WHEN status = 'ACTIVE' THEN 'LEFT' ELSE 'REJECTED' END,
                left_at = CASE WHEN status = 'ACTIVE' THEN GREATEST(:now, COALESCE(joined_at, invited_at)) ELSE left_at END,
                version = version + 1
            WHERE team_id = :teamId AND status IN ('ACTIVE', 'PENDING')
            """, nativeQuery = true)
    int disbandRemaining(@Param("teamId") UUID teamId, @Param("now") LocalDateTime now);

    /** 1 dòng thành viên + user (response sau khi chấp nhận lời mời). */
    @Query(value = """
            SELECT m.id AS memberId, m.team_id AS teamId, m.user_id AS userId,
                   u.display_name AS displayName, u.avatar_url AS avatarUrl, (u.deleted_at IS NOT NULL) AS userDeleted,
                   m.role AS role, m.status AS status, m.joined_at AS joinedAt
            FROM team_member m
            JOIN "user" u ON u.id = m.user_id
            WHERE m.id = :memberId
            """, nativeQuery = true)
    Optional<MemberView> findMemberViewById(@Param("memberId") UUID memberId);

    /**
     * Xoá tài khoản — bước khoá: nạp mọi membership của user có trạng thái trong {@code statuses} và giữ khoá dòng
     * ({@code PESSIMISTIC_WRITE} → PostgreSQL {@code FOR NO KEY UPDATE}) tới hết transaction. Thao tác ghi khác trên
     * các row này phải chờ: luồng ghi entity (accept, transfer, leave) sau khi xoá commit thấy {@code version} lệch →
     * retry; bulk {@link #disbandRemaining} đánh giá lại {@code WHERE} trên bản mới và bỏ qua row đã đóng. Sắp theo
     * {@code id} để thứ tự khoá ổn định trong phạm vi một user. Vẫn có thể deadlock với transaction khác khoá các row
     * theo thứ tự khác (vd chuyển quyền/giải tán trên đội có chung thành viên) — caller chạy qua retrier để thử lại.
     * Phải gọi trong transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<TeamMember> findByUserIdAndStatusInOrderByIdAsc(UUID userId, Collection<TeamMember.Status> statuses);

    /**
     * Xoá tài khoản — một câu cho mọi membership mở của user: MEMBER ACTIVE → LEFT, PENDING → REJECTED
     * (không set left_at). Không đụng row CAPTAIN (lớp an toàn — caller đã từ chối captain trên dữ liệu đã khoá).
     * {@code left_at = GREATEST(:now, COALESCE(joined_at, invited_at))} giữ CHECK left_at ≥ joined_at. Bulk update → tự
     * tăng version; xoá persistence context sau khi chạy để entity đã nạp ở bước khoá không còn trạng thái cũ.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE team_member
            SET status = CASE WHEN status = 'ACTIVE' THEN 'LEFT' ELSE 'REJECTED' END,
                left_at = CASE WHEN status = 'ACTIVE' THEN GREATEST(:now, COALESCE(joined_at, invited_at)) ELSE left_at END,
                version = version + 1
            WHERE user_id = :userId
              AND (status = 'PENDING' OR (status = 'ACTIVE' AND role = 'MEMBER'))
            """, nativeQuery = true)
    int leaveAndRejectAllOpen(@Param("userId") UUID userId, @Param("now") LocalDateTime now);

    /**
     * Lời mời PENDING gửi tới {@code userId} của đội chưa giải tán (giải tán đã chuyển PENDING sang REJECTED; điều
     * kiện đội là lớp an toàn), kèm đội trưởng ACTIVE hiện tại (cờ đã xoá để mapper che tên) và số thành viên ACTIVE.
     * Sắp invited_at mới nhất trước, rồi id giảm dần.
     */
    @Query(value = """
            SELECT m.id AS invitationId, m.invited_at AS invitedAt,
                   t.id AS teamId, t.name AS teamName, t.avatar_url AS avatarUrl, t.skill_levels AS skillLevels,
                   t.status AS status, cu.display_name AS captainName, (cu.deleted_at IS NOT NULL) AS captainDeleted,
                   (SELECT COUNT(*) FROM team_member am WHERE am.team_id = t.id AND am.status = 'ACTIVE') AS memberCount
            FROM team_member m
            JOIN team t ON t.id = m.team_id
            LEFT JOIN team_member cm ON cm.team_id = t.id AND cm.role = 'CAPTAIN' AND cm.status = 'ACTIVE'
            LEFT JOIN "user" cu ON cu.id = cm.user_id
            WHERE m.user_id = :userId AND m.status = 'PENDING' AND t.status <> 'DISBANDED'
            ORDER BY m.invited_at DESC, m.id DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM team_member m JOIN team t ON t.id = m.team_id
            WHERE m.user_id = :userId AND m.status = 'PENDING' AND t.status <> 'DISBANDED'
            """,
            nativeQuery = true)
    Page<MyInvitationView> findMyPendingInvitations(@Param("userId") UUID userId, Pageable pageable);

    /**
     * Đội {@code userId} đang là thành viên ACTIVE (tư cách ACTIVE ⇒ đội chưa giải tán). {@code role} null = mọi vai
     * trò; "CAPTAIN" = đúng tập đội đang chặn xoá tài khoản. Đọc không khoá (khác method PESSIMISTIC_WRITE của luồng
     * xoá tài khoản). Sắp đội làm đội trưởng trước, rồi joined_at mới nhất, rồi team id giảm dần.
     */
    @Query(value = """
            SELECT t.id AS teamId, t.name AS teamName, t.avatar_url AS avatarUrl, t.skill_levels AS skillLevels,
                   t.status AS status, cu.display_name AS captainName, (cu.deleted_at IS NOT NULL) AS captainDeleted,
                   (SELECT COUNT(*) FROM team_member am WHERE am.team_id = t.id AND am.status = 'ACTIVE') AS memberCount,
                   m.role AS role, m.joined_at AS joinedAt
            FROM team_member m
            JOIN team t ON t.id = m.team_id
            LEFT JOIN team_member cm ON cm.team_id = t.id AND cm.role = 'CAPTAIN' AND cm.status = 'ACTIVE'
            LEFT JOIN "user" cu ON cu.id = cm.user_id
            WHERE m.user_id = :userId AND m.status = 'ACTIVE'
              AND (CAST(:role AS VARCHAR) IS NULL OR m.role = CAST(:role AS VARCHAR))
            ORDER BY CASE WHEN m.role = 'CAPTAIN' THEN 0 ELSE 1 END, m.joined_at DESC, t.id DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM team_member m
            WHERE m.user_id = :userId AND m.status = 'ACTIVE'
              AND (CAST(:role AS VARCHAR) IS NULL OR m.role = CAST(:role AS VARCHAR))
            """,
            nativeQuery = true)
    Page<MyTeamView> findMyTeams(@Param("userId") UUID userId, @Param("role") String role, Pageable pageable);
}
