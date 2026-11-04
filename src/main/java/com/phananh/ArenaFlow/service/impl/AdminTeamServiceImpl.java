package com.phananh.ArenaFlow.service.impl;

import com.phananh.ArenaFlow.common.OptimisticLockRetrier;
import com.phananh.ArenaFlow.common.PageRequestFactory;
import com.phananh.ArenaFlow.dto.request.MarkInactiveRequest;
import com.phananh.ArenaFlow.dto.response.AdminTeamSummaryResponse;
import com.phananh.ArenaFlow.dto.response.PageResponse;
import com.phananh.ArenaFlow.dto.response.TeamResponse;
import com.phananh.ArenaFlow.entity.team.Team;
import com.phananh.ArenaFlow.entity.team.TeamMember;
import com.phananh.ArenaFlow.exception.BusinessException;
import com.phananh.ArenaFlow.exception.ErrorCode;
import com.phananh.ArenaFlow.mapper.TeamMapper;
import com.phananh.ArenaFlow.repository.TeamMemberRepository;
import com.phananh.ArenaFlow.repository.TeamRepository;
import com.phananh.ArenaFlow.service.AdminTeamService;
import com.phananh.ArenaFlow.service.TeamService;
import com.phananh.ArenaFlow.service.TeamStatusFilter;
import com.phananh.ArenaFlow.service.support.TeamGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminTeamServiceImpl implements AdminTeamService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final OptimisticLockRetrier optimisticLockRetrier;
    private final TeamMapper teamMapper;
    private final PageRequestFactory pageRequestFactory;
    private final TeamGuard teamGuard;
    private final TeamService teamService;

    /** (retry) S team → TEAM_001 → đã INACTIVE TEAM_011 → U (@Version) → S detail. */
    @Override
    public TeamResponse markInactive(UUID teamId, MarkInactiveRequest request) {
        return optimisticLockRetrier.execute(() -> {
            Team team = teamGuard.findTeam(teamId);
            if (team.getStatus() == Team.TeamStatus.INACTIVE) {
                throw new BusinessException(ErrorCode.TEAM_011);
            }
            team.setStatus(Team.TeamStatus.INACTIVE);
            team.setInactiveReason(request.reason().trim());
            teamRepository.saveAndFlush(team);
            return teamService.getTeam(teamId);
        });
    }

    /** (retry) S team → TEAM_001 → đang ACTIVE TEAM_012 → S captain ACTIVE → TEAM_013 → U → S detail. */
    @Override
    public TeamResponse reactivate(UUID teamId) {
        return optimisticLockRetrier.execute(() -> {
            Team team = teamGuard.findTeam(teamId);
            if (team.getStatus() == Team.TeamStatus.ACTIVE) {
                throw new BusinessException(ErrorCode.TEAM_012);
            }
            if (teamMemberRepository.findByTeamIdAndRoleAndStatus(teamId, TeamMember.Role.CAPTAIN,
                    TeamMember.Status.ACTIVE).isEmpty()) {
                throw new BusinessException(ErrorCode.TEAM_013);
            }
            team.setStatus(Team.TeamStatus.ACTIVE);
            team.setInactiveReason(null);
            teamRepository.saveAndFlush(team);
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

}
