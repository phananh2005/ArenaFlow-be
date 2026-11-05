package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.entity.team.Team;
import com.phananh.arenaflow.domain.projection.TeamDetailView;
import com.phananh.arenaflow.domain.projection.TeamSummaryView;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TeamRepository extends JpaRepository<Team, UUID> {

    /** Đọc đội để mời thành viên; {@code version} tăng lúc commit để tuần tự hoá với disband/invite (race M1, ADR-0003). */
    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("SELECT t FROM Team t WHERE t.id = :id")
    Optional<Team> findForInviteById(@Param("id") UUID id);

    /**
     * Trạng thái đã commit của đội (native → không lấy từ persistence context). Dùng ở nhánh lỗi của guard để phân biệt
     * "đội vừa bị giải tán" (TEAM_016) với lỗi quyền/tư cách khi disband commit chen giữa các câu đọc (READ COMMITTED).
     */
    @Query(value = "SELECT status FROM team WHERE id = :id", nativeQuery = true)
    Optional<String> findCurrentStatusById(@Param("id") UUID id);

    @Query(value = """
            SELECT t.id AS teamId, t.name AS teamName, t.avatar_url AS avatarUrl, t.description AS description,
                   t.skill_levels AS skillLevels, t.status AS status, t.inactive_reason AS inactiveReason,
                   cu.id AS captainId, cu.display_name AS captainName,
                   (cu.deleted_at IS NOT NULL) AS captainDeleted,
                   (SELECT COUNT(*) FROM team_member am WHERE am.team_id = t.id AND am.status = 'ACTIVE') AS memberCount,
                   t.created_by AS createdBy, t.created_at AS createdAt, t.updated_at AS updatedAt,
                   t.disband_reason AS disbandReason, t.disbanded_at AS disbandedAt, t.disbanded_by AS disbandedById,
                   du.display_name AS disbandedByName, (du.deleted_at IS NOT NULL) AS disbandedByDeleted
            FROM team t
            LEFT JOIN team_member cm ON cm.team_id = t.id AND cm.role = 'CAPTAIN' AND cm.status = 'ACTIVE'
            LEFT JOIN "user" cu ON cu.id = cm.user_id
            LEFT JOIN "user" du ON du.id = t.disbanded_by
            WHERE t.id = :teamId
            """, nativeQuery = true)
    Optional<TeamDetailView> findDetailById(@Param("teamId") UUID teamId);

    /** {@code status} null = tất cả. Sort cố định: created_at DESC, id DESC (index idx_team_status_created). */
    @Query(value = """
            SELECT t.id AS teamId, t.name AS teamName, t.avatar_url AS avatarUrl,
                   t.skill_levels AS skillLevels, t.status AS status, t.inactive_reason AS inactiveReason,
                   cu.display_name AS captainName, (cu.deleted_at IS NOT NULL) AS captainDeleted,
                   (SELECT COUNT(*) FROM team_member am WHERE am.team_id = t.id AND am.status = 'ACTIVE') AS memberCount,
                   t.created_at AS createdAt,
                   du.display_name AS disbandedByName, (du.deleted_at IS NOT NULL) AS disbandedByDeleted
            FROM team t
            LEFT JOIN team_member cm ON cm.team_id = t.id AND cm.role = 'CAPTAIN' AND cm.status = 'ACTIVE'
            LEFT JOIN "user" cu ON cu.id = cm.user_id
            LEFT JOIN "user" du ON du.id = t.disbanded_by
            WHERE (CAST(:status AS VARCHAR) IS NULL OR t.status = CAST(:status AS VARCHAR))
            ORDER BY t.created_at DESC, t.id DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM team t
            WHERE (CAST(:status AS VARCHAR) IS NULL OR t.status = CAST(:status AS VARCHAR))
            """,
            nativeQuery = true)
    Page<TeamSummaryView> findSummaries(@Param("status") String status, Pageable pageable);
}
