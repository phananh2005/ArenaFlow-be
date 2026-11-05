package com.phananh.arenaflow.application.support;

import com.phananh.arenaflow.domain.entity.team.Team;
import com.phananh.arenaflow.domain.entity.team.TeamMember;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.repository.TeamMemberRepository;
import com.phananh.arenaflow.domain.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Guard dùng chung cho thao tác trên đội — một nguồn cho thứ tự kiểm tra (USER-002 API_Spec §2.1a):
 * tồn tại (TEAM_001) → đội DISBANDED (TEAM_016) → đội INACTIVE (TEAM_015) → caller là captain ACTIVE (TEAM_002).
 */
@Component
@RequiredArgsConstructor
public class TeamGuard {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;

    /**
     * Đội tồn tại (TEAM_001), KHÔNG kiểm trạng thái. Private: thao tác ghi phải đi qua {@link #activeTeam(UUID)} hoặc
     * {@link #notDisbandedTeam(UUID)} để không bỏ sót TEAM_016.
     */
    private Team findTeam(UUID teamId) {
        return teamRepository.findById(teamId).orElseThrow(() -> new BusinessException(ErrorCode.TEAM_001));
    }

    /** Đội tồn tại, không DISBANDED, không INACTIVE (dùng trước mọi thao tác ghi lên đội). */
    public Team activeTeam(UUID teamId) {
        return requireActiveState(findTeam(teamId));
    }

    /**
     * Như {@link #activeTeam(UUID)} nhưng đọc đội kèm OPTIMISTIC_FORCE_INCREMENT: {@code team.version} tăng lúc commit,
     * nên invite xung đột với disband/invite khác trên cùng đội (race M1). Chỉ dùng cho mời thành viên.
     */
    public Team activeTeamForInvite(UUID teamId) {
        Team team = teamRepository.findForInviteById(teamId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_001));
        return requireActiveState(team);
    }

    /** Đội tồn tại và chưa DISBANDED; cho phép INACTIVE (giải tán, rời đội, thao tác admin). */
    public Team notDisbandedTeam(UUID teamId) {
        Team team = findTeam(teamId);
        requireNotDisbanded(team);
        return team;
    }

    /** Chỉ kiểm tồn tại, không kiểm trạng thái — chỉ dùng cho thao tác đọc (danh sách thành viên, lịch sử). */
    public void requireTeam(UUID teamId) {
        if (!teamRepository.existsById(teamId)) {
            throw new BusinessException(ErrorCode.TEAM_001);
        }
    }

    private static Team requireActiveState(Team team) {
        requireNotDisbanded(team);
        if (team.getStatus() == Team.TeamStatus.INACTIVE) {
            throw new BusinessException(ErrorCode.TEAM_015);
        }
        return team;
    }

    private static void requireNotDisbanded(Team team) {
        if (team.getStatus() == Team.TeamStatus.DISBANDED) {
            throw new BusinessException(ErrorCode.TEAM_016);
        }
    }

    /**
     * Lỗi cho guard thất bại SAU khi đã đọc đội ở Q1: nếu đội vừa bị giải tán bởi transaction khác (commit chen giữa các
     * câu đọc, READ COMMITTED) thì báo TEAM_016 thay cho {@code fallback} (TEAM_002/007/010/013). Thêm 1 câu đọc chỉ ở
     * nhánh lỗi. USER-002 Processing_Flow §5.
     */
    public BusinessException disbandedOr(UUID teamId, ErrorCode fallback) {
        boolean disbanded = teamRepository.findCurrentStatusById(teamId)
                .filter(Team.TeamStatus.DISBANDED.name()::equals)
                .isPresent();
        return new BusinessException(disbanded ? ErrorCode.TEAM_016 : fallback);
    }

    /** Caller phải là captain ACTIVE của đội. */
    public TeamMember requireCaptain(UUID teamId, UUID userId) {
        return teamMemberRepository
                .findByTeamIdAndRoleAndStatus(teamId, TeamMember.Role.CAPTAIN, TeamMember.Status.ACTIVE)
                .filter(captain -> captain.getUserId().equals(userId))
                .orElseThrow(() -> disbandedOr(teamId, ErrorCode.TEAM_002));
    }
}
