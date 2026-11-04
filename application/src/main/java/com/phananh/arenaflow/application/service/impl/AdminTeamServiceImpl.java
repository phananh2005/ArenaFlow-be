package com.phananh.arenaflow.application.service.impl;

import com.phananh.arenaflow.application.support.OptimisticLockRetrier;
import com.phananh.arenaflow.application.support.PageRequestFactory;
import com.phananh.arenaflow.application.dto.request.MarkInactiveRequest;
import com.phananh.arenaflow.application.dto.response.AdminTeamSummaryResponse;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.dto.response.TeamResponse;
import com.phananh.arenaflow.domain.entity.team.Team;
import com.phananh.arenaflow.domain.entity.team.TeamMember;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.application.mapper.TeamMapper;
import com.phananh.arenaflow.domain.repository.TeamMemberRepository;
import com.phananh.arenaflow.domain.repository.TeamRepository;
import com.phananh.arenaflow.application.service.AdminTeamService;
import com.phananh.arenaflow.application.service.TeamService;
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
