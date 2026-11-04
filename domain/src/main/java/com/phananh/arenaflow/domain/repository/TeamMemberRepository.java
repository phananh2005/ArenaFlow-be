package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.entity.team.TeamMember;
import com.phananh.arenaflow.domain.projection.CaptainTeamView;
import com.phananh.arenaflow.domain.projection.MemberView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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

    /** 1 truy vấn: các đội user đang là captain ACTIVE + số thành viên ACTIVE (subquery). */
    @Query(value = """
            SELECT m.team_id AS teamId,
                   (SELECT COUNT(*) FROM team_member am WHERE am.team_id = m.team_id AND am.status = 'ACTIVE') AS activeCount
            FROM team_member m
            WHERE m.user_id = :userId AND m.role = 'CAPTAIN' AND m.status = 'ACTIVE'
            """, nativeQuery = true)
    List<CaptainTeamView> findCaptainTeamsWithActiveCount(@Param("userId") UUID userId);

    /** Sort cố định: captain trước, joined_at tăng dần, user_id. */
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

    /** Xoá tài khoản: ACTIVE → LEFT. Bulk update → tự tăng version. */
    @Modifying
    @Query(value = """
            UPDATE team_member SET status = 'LEFT', left_at = :now, version = version + 1
            WHERE user_id = :userId AND status = 'ACTIVE'
            """, nativeQuery = true)
    int leaveAllActive(@Param("userId") UUID userId, @Param("now") OffsetDateTime now);

    /** Xoá tài khoản: PENDING → REJECTED. Bulk update → tự tăng version. */
    @Modifying
    @Query(value = """
            UPDATE team_member SET status = 'REJECTED', version = version + 1
            WHERE user_id = :userId AND status = 'PENDING'
            """, nativeQuery = true)
    int rejectAllPending(@Param("userId") UUID userId);
}
