package com.phananh.ArenaFlow.service.impl;

import com.phananh.ArenaFlow.common.OptimisticLockRetrier;
import com.phananh.ArenaFlow.common.PageRequestFactory;
import com.phananh.ArenaFlow.dto.request.InviteMemberRequest;
import com.phananh.ArenaFlow.dto.response.InvitationResponse;
import com.phananh.ArenaFlow.dto.response.MemberResponse;
import com.phananh.ArenaFlow.dto.response.PageResponse;
import com.phananh.ArenaFlow.entity.team.Team;
import com.phananh.ArenaFlow.entity.team.TeamMember;
import com.phananh.ArenaFlow.exception.BusinessException;
import com.phananh.ArenaFlow.exception.ErrorCode;
import com.phananh.ArenaFlow.mapper.MembershipMapper;
import com.phananh.ArenaFlow.repository.TeamMemberRepository;
import com.phananh.ArenaFlow.repository.UserRepository;
import com.phananh.ArenaFlow.service.TeamMembershipService;
import com.phananh.ArenaFlow.service.support.TeamGuard;
import org.springframework.dao.DataIntegrityViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TeamMembershipServiceImpl implements TeamMembershipService {

    private static final List<TeamMember.Status> OPEN_MEMBERSHIP =
            List.of(TeamMember.Status.PENDING, TeamMember.Status.ACTIVE);

    private final UserRepository userRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final OptimisticLockRetrier optimisticLockRetrier;
    private final MembershipMapper membershipMapper;
    private final PageRequestFactory pageRequestFactory;
    private final TeamGuard teamGuard;

    /** 2.12 — S team → TEAM_001/015 → S captain → TEAM_002 → S invitee → USER_001 → S tồn tại → TEAM_003 → I. */
    @Override
    @Transactional
    public InvitationResponse inviteMember(UUID teamId, UUID captainUserId, InviteMemberRequest request) {
        Team team = teamGuard.activeTeam(teamId);
        teamGuard.requireCaptain(teamId, captainUserId);
        if (userRepository.findByIdAndDeletedAtIsNull(request.userId()).isEmpty()) {
            throw new BusinessException(ErrorCode.USER_001);
        }
        if (teamMemberRepository.existsByTeamIdAndUserIdAndStatusIn(teamId, request.userId(), OPEN_MEMBERSHIP)) {
            throw new BusinessException(ErrorCode.TEAM_003);
        }
        TeamMember invitation = new TeamMember();
        invitation.setTeamId(teamId);
        invitation.setUserId(request.userId());
        invitation.setRole(TeamMember.Role.MEMBER);
        invitation.setStatus(TeamMember.Status.PENDING);
        try {
            return membershipMapper.toInvitation(teamMemberRepository.saveAndFlush(invitation), team.getName());
        } catch (DataIntegrityViolationException concurrentInvite) {
            // Unique (team_id, user_id) WHERE status IN ('PENDING','ACTIVE') — mời trùng song song.
            throw new BusinessException(ErrorCode.TEAM_003);
        }
    }

    /** 2.13 (retry) — S member → TEAM_004/005 → S team → TEAM_015 → TEAM_006 → U ACTIVE → S MemberView. */
    @Override
    public MemberResponse acceptInvitation(UUID invitationId, UUID userId) {
        return optimisticLockRetrier.execute(() -> {
            TeamMember invitation = pendingInvitationOf(invitationId, userId);
            invitation.setStatus(TeamMember.Status.ACTIVE);
            invitation.setJoinedAt(OffsetDateTime.now());
            teamMemberRepository.saveAndFlush(invitation);
            return teamMemberRepository.findMemberViewById(invitationId).map(membershipMapper::toMember)
                    .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_004));
        });
    }

    /** 2.14 (retry) — guard như 2.13 → U REJECTED; tên đội lấy từ team đã đọc. */
    @Override
    public InvitationResponse rejectInvitation(UUID invitationId, UUID userId) {
        return optimisticLockRetrier.execute(() -> {
            TeamMember invitation = teamMemberRepository.findById(invitationId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_004));
            Team team = guardInvitation(invitation, userId);
            invitation.setStatus(TeamMember.Status.REJECTED);
            teamMemberRepository.saveAndFlush(invitation);
            return membershipMapper.toInvitation(invitation, team.getName());
        });
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MemberResponse> listMembers(UUID teamId, int page, int pageSize) {
        teamGuard.requireTeam(teamId);
        return PageResponse.from(teamMemberRepository.findActiveMembers(teamId, pageRequestFactory.create(page, pageSize)),
                membershipMapper::toMember, page, pageSize);
    }

    /** 2.18 (retry) — S team → TEAM_001 → S member ACTIVE → TEAM_010 → captain → TEAM_009 → U LEFT. */
    @Override
    public void leaveTeam(UUID teamId, UUID userId) {
        optimisticLockRetrier.execute(() -> {
            teamGuard.requireTeam(teamId);
            TeamMember member = teamMemberRepository
                    .findByTeamIdAndUserIdAndStatus(teamId, userId, TeamMember.Status.ACTIVE)
                    .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_010));
            if (member.getRole() == TeamMember.Role.CAPTAIN) {
                throw new BusinessException(ErrorCode.TEAM_009);
            }
            member.setStatus(TeamMember.Status.LEFT);
            member.setLeftAt(OffsetDateTime.now());
            teamMemberRepository.saveAndFlush(member);
            return null;
        });
    }

    private TeamMember pendingInvitationOf(UUID invitationId, UUID userId) {
        TeamMember invitation = teamMemberRepository.findById(invitationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_004));
        guardInvitation(invitation, userId);
        return invitation;
    }

    private Team guardInvitation(TeamMember invitation, UUID userId) {
        if (!invitation.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.TEAM_005);
        }
        Team team = teamGuard.activeTeam(invitation.getTeamId());
        if (invitation.getStatus() != TeamMember.Status.PENDING) {
            throw new BusinessException(ErrorCode.TEAM_006);
        }
        return team;
    }
}
