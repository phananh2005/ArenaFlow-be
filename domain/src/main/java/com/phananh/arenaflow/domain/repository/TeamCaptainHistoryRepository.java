package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.entity.team.TeamCaptainHistory;
import com.phananh.arenaflow.domain.projection.CaptainHistoryView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface TeamCaptainHistoryRepository extends JpaRepository<TeamCaptainHistory, UUID> {

    /** Sort cố định: transferred_at DESC, id DESC (index team_id, transferred_at DESC). */
    @Query(value = """
            SELECT h.id AS historyId, h.team_id AS teamId,
                   h.from_user_id AS fromUserId, fu.display_name AS fromUserName,
                   (fu.deleted_at IS NOT NULL) AS fromUserDeleted,
                   h.to_user_id AS toUserId, tu.display_name AS toUserName,
                   (tu.deleted_at IS NOT NULL) AS toUserDeleted,
                   h.reason AS reason, h.transferred_at AS transferredAt
            FROM team_captain_history h
            LEFT JOIN "user" fu ON fu.id = h.from_user_id
            LEFT JOIN "user" tu ON tu.id = h.to_user_id
            WHERE h.team_id = :teamId
            ORDER BY h.transferred_at DESC, h.id DESC
            """,
            countQuery = "SELECT COUNT(*) FROM team_captain_history h WHERE h.team_id = :teamId",
            nativeQuery = true)
    Page<CaptainHistoryView> findHistory(@Param("teamId") UUID teamId, Pageable pageable);
}
