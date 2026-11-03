package com.phananh.ArenaFlow.service.impl;

import com.phananh.ArenaFlow.common.OptimisticLockRetrier;
import com.phananh.ArenaFlow.common.PageRequestFactory;
import com.phananh.ArenaFlow.dto.request.CreateTeamRequest;
import com.phananh.ArenaFlow.dto.request.TransferCaptainRequest;
import com.phananh.ArenaFlow.dto.request.UpdateTeamRequest;
import com.phananh.ArenaFlow.dto.response.CaptainHistoryResponse;
import com.phananh.ArenaFlow.dto.response.CaptainTransferResponse;
import com.phananh.ArenaFlow.dto.response.PageResponse;
import com.phananh.ArenaFlow.dto.response.TeamResponse;
import com.phananh.ArenaFlow.dto.response.TeamSummaryResponse;
import com.phananh.ArenaFlow.entity.team.Team;
import com.phananh.ArenaFlow.entity.team.TeamCaptainHistory;
import com.phananh.ArenaFlow.entity.team.TeamMember;
import com.phananh.ArenaFlow.entity.user.User;
import com.phananh.ArenaFlow.exception.BusinessException;
import com.phananh.ArenaFlow.exception.ErrorCode;
import com.phananh.ArenaFlow.mapper.TeamMapper;
import com.phananh.ArenaFlow.repository.TeamCaptainHistoryRepository;
import com.phananh.ArenaFlow.repository.TeamMemberRepository;
import com.phananh.ArenaFlow.repository.TeamRepository;
import com.phananh.ArenaFlow.repository.UserRepository;
import com.phananh.ArenaFlow.service.TeamService;
import com.phananh.ArenaFlow.service.TeamStatusFilter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class TeamServiceImpl implements TeamService {

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamCaptainHistoryRepository historyRepository;
    private final OptimisticLockRetrier optimisticLockRetrier;
    private final TeamMapper teamMapper;
    private final PageRequestFactory pageRequestFactory;

    public TeamServiceImpl(UserRepository userRepository, TeamRepository teamRepository,
                           TeamMemberRepository teamMemberRepository, TeamCaptainHistoryRepository historyRepository,
                           OptimisticLockRetrier optimisticLockRetrier, TeamMapper teamMapper,
                           PageRequestFactory pageRequestFactory) {
        this.userRepository = userRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.historyRepository = historyRepository;
        this.optimisticLockRetrier = optimisticLockRetrier;
        this.teamMapper = teamMapper;
        this.pageRequestFactory = pageRequestFactory;
    }

    /** 2.8 — S user, I team, I captain member, I history; response dựng từ dữ liệu đã có (memberCount = 1). */
    @Override
    @Transactional
    public TeamResponse createTeam(UUID userId, CreateTeamRequest request) {
        User creator = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_001));
        OffsetDateTime now = OffsetDateTime.now();

        Team team = new Team();
        team.setName(request.teamName().trim());
        team.setAvatarUrl(request.avatarUrl());
        team.setDescription(request.description());
        team.setSkillLevels(request.skillLevels() == null ? List.of() : request.skillLevels());
        team.setCreatedBy(userId);
        Team saved = teamRepository.save(team);

        TeamMember captain = new TeamMember();
        captain.setTeamId(saved.getId());
        captain.setUserId(userId);
        captain.setRole(TeamMember.Role.CAPTAIN);
        captain.setStatus(TeamMember.Status.ACTIVE);
        captain.setInvitedAt(now); // joined_at >= invited_at (CHECK) → cùng một mốc thời gian
        captain.setJoinedAt(now);
        teamMemberRepository.save(captain);

        historyRepository.save(history(saved.getId(), null, userId, null, now));

        return new TeamResponse(saved.getId(), saved.getName(), saved.getAvatarUrl(), saved.getDescription(),
                saved.getSkillLevels(), saved.getStatus().name(), saved.getInactiveReason(), userId,
                creator.getDisplayName(), 1, userId, saved.getCreatedAt(), saved.getUpdatedAt());
    }

    @Override
    @Transactional(readOnly = true)
    public TeamResponse getTeam(UUID teamId) {
        return teamRepository.findDetailById(teamId).map(teamMapper::toResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_001));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TeamSummaryResponse> listTeams(TeamStatusFilter status, int page, int pageSize) {
        return PageResponse.from(
                teamRepository.findSummaries(status.toQueryValue(), pageRequestFactory.create(page, pageSize)),
                teamMapper::toSummary, page, pageSize);
    }

    /** 2.11 (retry) — S team → TEAM_001 → INACTIVE TEAM_015 → S captain → TEAM_002 → U team (@Version) → S detail. */
    @Override
    public TeamResponse updateTeam(UUID teamId, UUID userId, UpdateTeamRequest request) {
        return optimisticLockRetrier.execute(() -> {
            Team team = activeTeam(teamId);
            requireCaptain(teamId, userId);
            if (request.teamName() != null) {
                team.setName(request.teamName().trim());
            }
            if (request.avatarUrl() != null) {
                team.setAvatarUrl(request.avatarUrl());
            }
            if (request.description() != null) {
                team.setDescription(request.description());
            }
            if (request.skillLevels() != null) {
                team.setSkillLevels(request.skillLevels());
            }
            teamRepository.saveAndFlush(team);
            return getTeam(teamId);
        });
    }

    /** 2.15 (retry) — S team, S captain, S member mới, U ×2 (@Version), I history = 6 (W6). */
    @Override
    public CaptainTransferResponse transferCaptain(UUID teamId, UUID userId, TransferCaptainRequest request) {
        return optimisticLockRetrier.execute(() -> {
            activeTeam(teamId);
            TeamMember oldCaptain = requireCaptain(teamId, userId);
            if (request.newCaptainId().equals(userId)) {
                throw new BusinessException(ErrorCode.TEAM_008);
            }
            TeamMember newCaptain = teamMemberRepository
                    .findByTeamIdAndUserIdAndStatus(teamId, request.newCaptainId(), TeamMember.Status.ACTIVE)
                    .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_007));
            oldCaptain.setRole(TeamMember.Role.MEMBER);
            newCaptain.setRole(TeamMember.Role.CAPTAIN);
            teamMemberRepository.saveAllAndFlush(List.of(oldCaptain, newCaptain));

            OffsetDateTime now = OffsetDateTime.now();
            historyRepository.save(history(teamId, userId, request.newCaptainId(), request.reason(), now));
            return new CaptainTransferResponse(teamId, userId, request.newCaptainId(), request.reason(), now);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CaptainHistoryResponse> listCaptainHistory(UUID teamId, int page, int pageSize) {
        if (!teamRepository.existsById(teamId)) {
            throw new BusinessException(ErrorCode.TEAM_001);
        }
        return PageResponse.from(historyRepository.findHistory(teamId, pageRequestFactory.create(page, pageSize)),
                teamMapper::toResponse, page, pageSize);
    }

    private Team activeTeam(UUID teamId) {
        Team team = teamRepository.findById(teamId).orElseThrow(() -> new BusinessException(ErrorCode.TEAM_001));
        if (team.getStatus() == Team.TeamStatus.INACTIVE) {
            throw new BusinessException(ErrorCode.TEAM_015);
        }
        return team;
    }

    private TeamMember requireCaptain(UUID teamId, UUID userId) {
        return teamMemberRepository
                .findByTeamIdAndRoleAndStatus(teamId, TeamMember.Role.CAPTAIN, TeamMember.Status.ACTIVE)
                .filter(captain -> captain.getUserId().equals(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_002));
    }

    private static TeamCaptainHistory history(UUID teamId, UUID fromUserId, UUID toUserId, String reason,
                                              OffsetDateTime transferredAt) {
        TeamCaptainHistory history = new TeamCaptainHistory();
        history.setTeamId(teamId);
        history.setFromUserId(fromUserId);
        history.setToUserId(toUserId);
        history.setReason(reason);
        history.setTransferredAt(transferredAt);
        return history;
    }
}
