package com.phananh.arenaflow.application.service.impl;

import com.phananh.arenaflow.application.support.ConcurrencyConflictRetrier;
import com.phananh.arenaflow.application.support.PageRequestFactory;
import com.phananh.arenaflow.application.dto.request.CreateTeamRequest;
import com.phananh.arenaflow.application.dto.request.DisbandTeamRequest;
import com.phananh.arenaflow.application.dto.request.TransferCaptainRequest;
import com.phananh.arenaflow.application.dto.request.UpdateTeamRequest;
import com.phananh.arenaflow.application.dto.response.CaptainHistoryResponse;
import com.phananh.arenaflow.application.dto.response.CaptainTransferResponse;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.dto.response.TeamResponse;
import com.phananh.arenaflow.application.dto.response.TeamSummaryResponse;
import com.phananh.arenaflow.domain.entity.team.Team;
import com.phananh.arenaflow.domain.entity.team.TeamCaptainHistory;
import com.phananh.arenaflow.domain.entity.team.TeamMember;
import com.phananh.arenaflow.domain.entity.user.User;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.application.mapper.TeamMapper;
import com.phananh.arenaflow.domain.repository.TeamCaptainHistoryRepository;
import com.phananh.arenaflow.domain.repository.TeamMemberRepository;
import com.phananh.arenaflow.domain.repository.TeamRepository;
import com.phananh.arenaflow.domain.repository.UserRepository;
import com.phananh.arenaflow.application.service.TeamService;
import com.phananh.arenaflow.application.service.TeamStatusFilter;
import com.phananh.arenaflow.application.support.TeamGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TeamServiceImpl implements TeamService {

    /** Lý do giải tán mặc định khi client không gửi hoặc gửi rỗng. */
    static final String DEFAULT_DISBAND_REASON = "Đội trưởng giải tán đội";

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamCaptainHistoryRepository historyRepository;
    private final ConcurrencyConflictRetrier concurrencyConflictRetrier;
    private final TeamMapper teamMapper;
    private final PageRequestFactory pageRequestFactory;
    private final TeamGuard teamGuard;

    /** S user, I team, I captain member, I history; response dựng từ dữ liệu đã có (memberCount = 1). */
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
                creator.getDisplayName(), 1, userId, saved.getCreatedAt(), saved.getUpdatedAt(),
                null, null, null, null);
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

    /**
     * (retry) S team → TEAM_001 → DISBANDED TEAM_016 → INACTIVE TEAM_015 → S captain → TEAM_002 → U team (@Version)
     * → S detail.
     */
    @Override
    public TeamResponse updateTeam(UUID teamId, UUID userId, UpdateTeamRequest request) {
        return concurrencyConflictRetrier.execute(() -> {
            Team team = teamGuard.activeTeam(teamId);
            teamGuard.requireCaptain(teamId, userId);
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

    /**
     * (retry) S team → TEAM_001/016/015 → S captain → TEAM_002 → TEAM_008 → S member mới → TEAM_007 → U ×2 (@Version)
     * → I history: 6 truy vấn. Member mới vừa bị chuyển LEFT do đội bị giải tán song song → TEAM_016 (disbandedOr).
     */
    @Override
    public CaptainTransferResponse transferCaptain(UUID teamId, UUID userId, TransferCaptainRequest request) {
        return concurrencyConflictRetrier.execute(() -> {
            teamGuard.activeTeam(teamId);
            TeamMember oldCaptain = teamGuard.requireCaptain(teamId, userId);
            if (request.newCaptainId().equals(userId)) {
                throw new BusinessException(ErrorCode.TEAM_008);
            }
            TeamMember newCaptain = teamMemberRepository
                    .findByTeamIdAndUserIdAndStatus(teamId, request.newCaptainId(), TeamMember.Status.ACTIVE)
                    .orElseThrow(() -> teamGuard.disbandedOr(teamId, ErrorCode.TEAM_007));
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
        teamGuard.requireTeam(teamId);
        return PageResponse.from(historyRepository.findHistory(teamId, pageRequestFactory.create(page, pageSize)),
                teamMapper::toResponse, page, pageSize);
    }

    /**
     * (retry) S team → TEAM_001/016 → S captain → TEAM_002 → U captain LEFT (@Version) → U team DISBANDED (@Version)
     * → U còn lại (ACTIVE → LEFT, PENDING → REJECTED) → S detail: 6 truy vấn. Đội INACTIVE vẫn giải tán được,
     * giữ nguyên inactive_reason. Mọi bước ghi lệch version → retry.
     */
    @Override
    public TeamResponse disbandTeam(UUID teamId, UUID captainUserId, DisbandTeamRequest request) {
        String reason = disbandReason(request);
        return concurrencyConflictRetrier.execute(() -> {
            Team team = teamGuard.notDisbandedTeam(teamId);
            TeamMember captain = teamGuard.requireCaptain(teamId, captainUserId);
            OffsetDateTime now = OffsetDateTime.now();
            captain.setStatus(TeamMember.Status.LEFT);
            captain.setLeftAt(now);
            teamMemberRepository.saveAndFlush(captain);

            team.setStatus(Team.TeamStatus.DISBANDED);
            team.setDisbandReason(reason);
            team.setDisbandedAt(now);
            team.setDisbandedBy(captainUserId);
            teamRepository.saveAndFlush(team);

            teamMemberRepository.disbandRemaining(teamId, now);
            return getTeam(teamId);
        });
    }

    private static String disbandReason(DisbandTeamRequest request) {
        String reason = request == null || request.reason() == null ? "" : request.reason().trim();
        return reason.isEmpty() ? DEFAULT_DISBAND_REASON : reason;
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
