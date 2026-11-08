package com.phananh.arenaflow.application.service.impl;

import com.phananh.arenaflow.application.support.ConcurrencyConflictRetrier;
import com.phananh.arenaflow.application.support.PageRequestFactory;
import com.phananh.arenaflow.application.dto.request.MarkInactiveRequest;
import com.phananh.arenaflow.application.dto.response.AdminTeamSummaryResponse;
import com.phananh.arenaflow.application.dto.response.MemberHistoryResponse;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.dto.response.TeamResponse;
import com.phananh.arenaflow.domain.entity.team.Team;
import com.phananh.arenaflow.domain.entity.team.TeamMember;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.application.mapper.MembershipMapper;
import com.phananh.arenaflow.application.mapper.TeamMapper;
import com.phananh.arenaflow.domain.repository.TeamMemberRepository;
import com.phananh.arenaflow.domain.repository.TeamRepository;
import com.phananh.arenaflow.application.service.AdminTeamService;
import com.phananh.arenaflow.application.service.TeamService;
import com.phananh.arenaflow.application.service.MemberHistoryStatusFilter;
import com.phananh.arenaflow.application.service.TeamStatusFilter;
import com.phananh.arenaflow.application.support.TeamGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminTeamServiceImpl implements AdminTeamService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final ConcurrencyConflictRetrier concurrencyConflictRetrier;
    private final TeamMapper teamMapper;
    private final MembershipMapper membershipMapper;
    private final PageRequestFactory pageRequestFactory;
    private final TeamGuard teamGuard;
    private final TeamService teamService;

    /** (retry) S team → TEAM_001 → TEAM_016 → đã INACTIVE TEAM_011 → U (@Version) → S detail. */
    @Override
    public TeamResponse markInactive(UUID teamId, MarkInactiveRequest request) {
        return concurrencyConflictRetrier.execute(() -> {
            Team team = teamGuard.notDisbandedTeam(teamId);
            if (team.getStatus() == Team.TeamStatus.INACTIVE) {
                throw new BusinessException(ErrorCode.TEAM_011);
            }
            team.setStatus(Team.TeamStatus.INACTIVE);
            team.setInactiveReason(request.reason().trim());
            teamRepository.save(team);
            // Hibernate tự flush thay đổi trên trước native query đọc lại response.
            return teamService.getTeam(teamId);
        });
    }

    /** (retry) S team → TEAM_001 → TEAM_016 → đang ACTIVE TEAM_012 → S captain ACTIVE → TEAM_013 → U → S detail. */
    @Override
    public TeamResponse reactivate(UUID teamId) {
        return concurrencyConflictRetrier.execute(() -> {
            Team team = teamGuard.notDisbandedTeam(teamId);
            if (team.getStatus() == Team.TeamStatus.ACTIVE) {
                throw new BusinessException(ErrorCode.TEAM_012);
            }
            if (teamMemberRepository.findByTeamIdAndRoleAndStatus(teamId, TeamMember.Role.CAPTAIN,
                    TeamMember.Status.ACTIVE).isEmpty()) {
                throw teamGuard.disbandedOr(teamId, ErrorCode.TEAM_013);
            }
            team.setStatus(Team.TeamStatus.ACTIVE);
            team.setInactiveReason(null);
            teamRepository.save(team);
            // Hibernate tự flush thay đổi trên trước native query đọc lại response.
            return teamService.getTeam(teamId);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminTeamSummaryResponse> listTeams(TeamStatusFilter status, int page, int pageSize) {
        return PageResponse.from(
                teamRepository.findSummaries(status.toQueryValue(), pageRequestFactory.create(page, pageSize)),
                teamMapper::toAdminSummary, page, pageSize);
    }

    /** S tồn tại → TEAM_001 → S trang + COUNT: 3 truy vấn. */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<MemberHistoryResponse> listMemberHistory(UUID teamId, MemberHistoryStatusFilter status,
                                                                 int page, int pageSize) {
        teamGuard.requireTeam(teamId);
        return PageResponse.from(teamMemberRepository.findMemberHistory(teamId, status.toQueryValues(),
                        pageRequestFactory.create(page, pageSize)),
                membershipMapper::toMemberHistory, page, pageSize);
    }

}
