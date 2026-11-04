package com.phananh.ArenaFlow.service.impl;

import com.phananh.ArenaFlow.common.OptimisticLockRetrier;
import com.phananh.ArenaFlow.dto.request.ProfileUpdateRequest;
import com.phananh.ArenaFlow.dto.response.ProfileResponse;
import com.phananh.ArenaFlow.entity.team.Team;
import com.phananh.ArenaFlow.entity.user.User;
import com.phananh.ArenaFlow.exception.BusinessException;
import com.phananh.ArenaFlow.exception.ErrorCode;
import com.phananh.ArenaFlow.mapper.ProfileMapper;
import com.phananh.ArenaFlow.repository.AdministrativeUnitRepository;
import com.phananh.ArenaFlow.repository.OAuthAccountRepository;
import com.phananh.ArenaFlow.repository.TeamMemberRepository;
import com.phananh.ArenaFlow.repository.TeamRepository;
import com.phananh.ArenaFlow.repository.UserRepository;
import com.phananh.ArenaFlow.repository.projection.CaptainTeamView;
import com.phananh.ArenaFlow.repository.projection.ProfileView;
import com.phananh.ArenaFlow.service.ProfileService;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    static final String SOLE_CAPTAIN_DELETED_REASON = "Đội trưởng duy nhất đã xoá tài khoản";

    private final UserRepository userRepository;
    private final OAuthAccountRepository oauthAccountRepository;
    private final AdministrativeUnitRepository administrativeUnitRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final OptimisticLockRetrier optimisticLockRetrier;
    private final ProfileMapper profileMapper;

    @Override
    @Transactional(readOnly = true)
    public ProfileResponse getProfile(UUID userId) {
        return toResponse(userId);
    }

    @Override
    @Transactional
    public ProfileResponse updateProfile(UUID userId, ProfileUpdateRequest request) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_001));
        if (request.wardId() != null && administrativeUnitRepository.findWardWithProvince(request.wardId()).isEmpty()) {
            throw new BusinessException(ErrorCode.USER_002);
        }
        applyChanges(user, request);
        userRepository.saveAndFlush(user);
        return toResponse(userId);
    }

    /**
     * Xoá mềm (ADR-0002). Mỗi lần thử là 1 transaction do retrier mở; markInactive thiếu dòng (có người vừa vào
     * đội) → ném ObjectOptimisticLockingFailureException để retrier thử lại → lần sau thấy activeCount > 1 → USER_003.
     */
    @Override
    public void deleteAccount(UUID userId) {
        optimisticLockRetrier.execute(() -> {
            List<CaptainTeamView> captainTeams = teamMemberRepository.findCaptainTeamsWithActiveCount(userId);
            if (captainTeams.stream().anyMatch(team -> team.getActiveCount() > 1)) {
                throw new BusinessException(ErrorCode.USER_003);
            }
            OffsetDateTime now = OffsetDateTime.now();
            Set<UUID> soloTeamIds = captainTeams.stream().map(CaptainTeamView::getTeamId).collect(Collectors.toSet());
            if (!soloTeamIds.isEmpty()
                    && teamRepository.markInactiveByIds(soloTeamIds, SOLE_CAPTAIN_DELETED_REASON, now) < soloTeamIds.size()) {
                throw new ObjectOptimisticLockingFailureException(Team.class, soloTeamIds);
            }
            teamMemberRepository.leaveAllActive(userId, now);
            teamMemberRepository.rejectAllPending(userId);
            userRepository.softDelete(userId, now);
            return null;
        });
    }

    private ProfileResponse toResponse(UUID userId) {
        ProfileView view = userRepository.findProfileViewById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_001));
        return profileMapper.toResponse(view, oauthAccountRepository.findByUserId(userId));
    }

    private static void applyChanges(User user, ProfileUpdateRequest request) {
        if (request.displayName() != null) {
            user.setDisplayName(request.displayName().trim());
        }
        if (request.avatarUrl() != null) {
            user.setAvatarUrl(request.avatarUrl());
        }
        if (request.bio() != null) {
            user.setBio(request.bio());
        }
        if (request.preferredPosition() != null) {
            user.setPreferredPosition(request.preferredPosition());
        }
        if (request.dateOfBirth() != null) {
            user.setDateOfBirth(request.dateOfBirth());
        }
        if (request.gender() != null) {
            user.setGender(request.gender());
        }
        if (request.wardId() != null) {
            user.setWardId(request.wardId());
        }
    }
}
