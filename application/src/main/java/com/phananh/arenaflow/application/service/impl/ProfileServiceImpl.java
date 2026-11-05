package com.phananh.arenaflow.application.service.impl;

import com.phananh.arenaflow.application.support.OptimisticLockRetrier;
import com.phananh.arenaflow.application.dto.request.ProfileUpdateRequest;
import com.phananh.arenaflow.application.dto.response.ProfileResponse;
import com.phananh.arenaflow.domain.entity.team.TeamMember;
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
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    /** Membership còn hiệu lực của user — đối tượng khoá và cập nhật khi xoá tài khoản. */
    private static final List<TeamMember.Status> OPEN_MEMBERSHIP_STATUSES =
            List.of(TeamMember.Status.ACTIVE, TeamMember.Status.PENDING);

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
     * Xoá mềm tài khoản (ADR-0002) với quy tắc captain phải chuyển quyền/giải tán trước (ADR-0005).
     * <p>Khoá bi quan có phạm vi (ADR-0007), trong một transaction:
     * <ol>
     *   <li>Q1 khoá mọi membership ACTIVE/PENDING của user ({@code PESSIMISTIC_WRITE}, theo id) — thao tác song song trên các
     *       row này (accept, transfer cho/khỏi user…) phải chờ tới khi transaction kết thúc.</li>
     *   <li>Còn là captain ACTIVE trên dữ liệu đã khoá → {@code 422 USER_003}, rollback, không ghi gì.</li>
     *   <li>Q2 một bulk UPDATE: MEMBER ACTIVE → LEFT, PENDING → REJECTED.</li>
     *   <li>Q3 soft delete user.</li>
     * </ol>
     * 3 truy vấn (W6). Không đổi trạng thái đội. Gọi lại khi đã xoá: không lỗi (idempotent).
     */
    @Override
    public void deleteAccount(UUID userId) {
        optimisticLockRetrier.execute(() -> {
            List<TeamMember> openMemberships = teamMemberRepository.findByUserIdAndStatusInOrderByIdAsc(userId,
                    OPEN_MEMBERSHIP_STATUSES);
            if (openMemberships.stream().anyMatch(ProfileServiceImpl::isActiveCaptain)) {
                throw new BusinessException(ErrorCode.USER_003);
            }
            OffsetDateTime now = OffsetDateTime.now();
            teamMemberRepository.leaveAndRejectAllOpen(userId, now);
            userRepository.softDelete(userId, now);
            return null;
        });
    }

    private static boolean isActiveCaptain(TeamMember membership) {
        return membership.getStatus() == TeamMember.Status.ACTIVE && membership.getRole() == TeamMember.Role.CAPTAIN;
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
