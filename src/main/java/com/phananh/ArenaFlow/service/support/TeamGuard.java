package com.phananh.ArenaFlow.service.support;

import com.phananh.ArenaFlow.entity.team.Team;
import com.phananh.ArenaFlow.entity.team.TeamMember;
import com.phananh.ArenaFlow.exception.BusinessException;
import com.phananh.ArenaFlow.exception.ErrorCode;
import com.phananh.ArenaFlow.repository.TeamMemberRepository;
import com.phananh.ArenaFlow.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Guard dùng chung cho thao tác trên đội — một nguồn cho thứ tự kiểm tra:
 * tồn tại (TEAM_001) → đội INACTIVE (TEAM_015) → caller là captain ACTIVE (TEAM_002).
 */
@Component
@RequiredArgsConstructor
public class TeamGuard {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;

    /** Đội tồn tại (TEAM_001), không kiểm trạng thái — dùng cho thao tác admin trên cả đội INACTIVE. */
    public Team findTeam(UUID teamId) {
        return teamRepository.findById(teamId).orElseThrow(() -> new BusinessException(ErrorCode.TEAM_001));
    }

    /** Đội tồn tại và không INACTIVE (dùng trước mọi thao tác ghi lên đội). */
    public Team activeTeam(UUID teamId) {
        Team team = findTeam(teamId);
        if (team.getStatus() == Team.TeamStatus.INACTIVE) {
            throw new BusinessException(ErrorCode.TEAM_015);
        }
        return team;
    }

    /** Chỉ kiểm tồn tại (đọc danh sách, rời đội). */
    public void requireTeam(UUID teamId) {
        if (!teamRepository.existsById(teamId)) {
            throw new BusinessException(ErrorCode.TEAM_001);
        }
    }

    /** Caller phải là captain ACTIVE của đội. */
    public TeamMember requireCaptain(UUID teamId, UUID userId) {
        return teamMemberRepository
                .findByTeamIdAndRoleAndStatus(teamId, TeamMember.Role.CAPTAIN, TeamMember.Status.ACTIVE)
                .filter(captain -> captain.getUserId().equals(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_002));
    }
}
