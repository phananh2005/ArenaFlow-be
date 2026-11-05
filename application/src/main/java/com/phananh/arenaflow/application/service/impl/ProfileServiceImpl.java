package com.phananh.arenaflow.application.service.impl;

import com.phananh.arenaflow.application.support.OptimisticLockRetrier;
import com.phananh.arenaflow.application.dto.request.ProfileUpdateRequest;
import com.phananh.arenaflow.application.dto.response.ProfileResponse;
import com.phananh.arenaflow.domain.entity.user.User;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.application.mapper.ProfileMapper;
import com.phananh.arenaflow.domain.repository.AdministrativeUnitRepository;
import com.phananh.arenaflow.domain.repository.OAuthAccountRepository;
import com.phananh.arenaflow.domain.repository.TeamMemberRepository;
import com.phananh.arenaflow.domain.repository.UserRepository;
import com.phananh.arenaflow.domain.projection.ProfileView;
import com.phananh.arenaflow.application.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private final UserRepository userRepository;
    private final OAuthAccountRepository oauthAccountRepository;
    private final AdministrativeUnitRepository administrativeUnitRepository;
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
     * Xoá mềm (ADR-0002, ADR-0005): (retry) S captain ACTIVE → USER_003 → U rời đội (chỉ MEMBER) → S captain ACTIVE
     * (kiểm lại race R1: vừa được chuyển quyền) → USER_003 → U từ chối lời mời → U soft delete = 5 (W6).
     * Không đổi trạng thái đội.
     */
    @Override
    public void deleteAccount(UUID userId) {
        optimisticLockRetrier.execute(() -> {
            requireNotActiveCaptain(userId);
            OffsetDateTime now = OffsetDateTime.now();
            teamMemberRepository.leaveAllActive(userId, now);
            requireNotActiveCaptain(userId);
            teamMemberRepository.rejectAllPending(userId);
            userRepository.softDelete(userId, now);
            return null;
        });
    }

    private void requireNotActiveCaptain(UUID userId) {
        if (teamMemberRepository.existsActiveCaptainByUserId(userId)) {
            throw new BusinessException(ErrorCode.USER_003);
        }
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
