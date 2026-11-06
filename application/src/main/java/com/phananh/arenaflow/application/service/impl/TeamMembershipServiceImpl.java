package com.phananh.arenaflow.application.service.impl;

import com.phananh.arenaflow.application.support.OptimisticLockRetrier;
import com.phananh.arenaflow.application.support.PageRequestFactory;
import com.phananh.arenaflow.application.dto.request.InviteMemberRequest;
import com.phananh.arenaflow.application.dto.response.InvitationResponse;
import com.phananh.arenaflow.application.dto.response.MemberResponse;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.domain.entity.team.Team;
import com.phananh.arenaflow.domain.entity.team.TeamMember;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.application.mapper.MembershipMapper;
import com.phananh.arenaflow.domain.repository.TeamMemberRepository;
import com.phananh.arenaflow.domain.repository.UserRepository;
import com.phananh.arenaflow.application.service.TeamMembershipService;
import com.phananh.arenaflow.application.support.TeamGuard;
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

    /**
     * (retry) S team (force increment) → TEAM_001/016/015 → S captain → TEAM_002 → S invitee → USER_001
     * → S tồn tại → TEAM_003 → I → U team.version lúc commit: 6 truy vấn. Tăng version để invite không lọt vào đội
     * vừa bị giải tán bởi transaction chạy song song; invite song song cùng đội có thể hết retry → TEAM_014.
     */
    @Override
    public InvitationResponse inviteMember(UUID teamId, UUID captainUserId, InviteMemberRequest request) {
        return optimisticLockRetrier.execute(() -> {
            Team team = teamGuard.activeTeamForInvite(teamId);
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
        });
    }

    /** (retry) S member → TEAM_004/005 → S team → TEAM_016/015 → TEAM_006 → U ACTIVE → S MemberView. */
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

    /** (retry) guard như acceptInvitation → U REJECTED; không trả kết quả (controller trả 204). */
    @Override
    public void rejectInvitation(UUID invitationId, UUID userId) {
        optimisticLockRetrier.execute(() -> {
            TeamMember invitation = pendingInvitationOf(invitationId, userId);
            invitation.setStatus(TeamMember.Status.REJECTED);
            teamMemberRepository.saveAndFlush(invitation);
            return null;
        });
    }

    /** Đội không tồn tại → TEAM_001; đọc được ở mọi trạng thái đội. Chỉ thành viên ACTIVE, captain đứng đầu. */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<MemberResponse> listMembers(UUID teamId, int page, int pageSize) {
        teamGuard.requireTeam(teamId);
        return PageResponse.from(teamMemberRepository.findActiveMembers(teamId, pageRequestFactory.create(page, pageSize)),
                membershipMapper::toMember, page, pageSize);
    }

    /** (retry) S team → TEAM_001/016 (INACTIVE vẫn rời được) → S member ACTIVE → TEAM_010 → captain → TEAM_009 → U LEFT. */
    @Override
    public void leaveTeam(UUID teamId, UUID userId) {
        optimisticLockRetrier.execute(() -> {
            teamGuard.notDisbandedTeam(teamId);
            TeamMember member = teamMemberRepository
                    .findByTeamIdAndUserIdAndStatus(teamId, userId, TeamMember.Status.ACTIVE)
                    .orElseThrow(() -> teamGuard.disbandedOr(teamId, ErrorCode.TEAM_010));
            if (member.getRole() == TeamMember.Role.CAPTAIN) {
                throw new BusinessException(ErrorCode.TEAM_009);
            }
            member.setStatus(TeamMember.Status.LEFT);
            member.setLeftAt(OffsetDateTime.now());
            teamMemberRepository.saveAndFlush(member);
            return null;
        });
    }

    /** S member (TEAM_004) → người được mời (TEAM_005) → S team (TEAM_016/015) → còn PENDING (TEAM_006). */
    private TeamMember pendingInvitationOf(UUID invitationId, UUID userId) {
        TeamMember invitation = teamMemberRepository.findById(invitationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_004));
        if (!invitation.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.TEAM_005);
        }
        teamGuard.activeTeam(invitation.getTeamId());
        if (invitation.getStatus() != TeamMember.Status.PENDING) {
            throw new BusinessException(ErrorCode.TEAM_006);
        }
        return invitation;
    }
}
