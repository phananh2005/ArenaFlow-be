package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.entity.team.TeamMember;
import com.phananh.arenaflow.domain.projection.MemberHistoryView;
import com.phananh.arenaflow.domain.projection.MemberView;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
    int disbandRemaining(@Param("teamId") UUID teamId, @Param("now") OffsetDateTime now);

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
     * {@code id} để thứ tự khoá ổn định (tránh deadlock). Phải gọi trong transaction.
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
    int leaveAndRejectAllOpen(@Param("userId") UUID userId, @Param("now") OffsetDateTime now);
}
