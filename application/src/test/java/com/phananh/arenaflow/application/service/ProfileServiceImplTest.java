package com.phananh.arenaflow.application.service;

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
import com.phananh.arenaflow.domain.projection.WardView;
import com.phananh.arenaflow.application.service.impl.ProfileServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.InOrder;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfileServiceImplTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final OAuthAccountRepository oauthAccountRepository = mock(OAuthAccountRepository.class);
    private final AdministrativeUnitRepository administrativeUnitRepository = mock(AdministrativeUnitRepository.class);
    private final TeamMemberRepository teamMemberRepository = mock(TeamMemberRepository.class);
    private final OptimisticLockRetrier retrier = mock(OptimisticLockRetrier.class);
    private final ProfileMapper profileMapper = Mappers.getMapper(ProfileMapper.class);
    private final SpelAwareProxyProjectionFactory projections = new SpelAwareProxyProjectionFactory();
    private final UUID userId = UUID.randomUUID();

    private ProfileServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new ProfileServiceImpl(userRepository, oauthAccountRepository, administrativeUnitRepository,
                teamMemberRepository, retrier, profileMapper);
        when(retrier.execute(any())).thenAnswer(invocation -> ((Supplier<Object>) invocation.getArgument(0)).get());
    }

    private ProfileView profileView(String displayName) {
        return projections.createProjection(ProfileView.class,
                Map.of("userId", userId, "role", "USER", "displayName", displayName));
    }

    private static void assertError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }

    @Test
    void getProfileMapsViewAndProvidersOrThrowsUser001() {
        when(userRepository.findProfileViewById(userId)).thenReturn(Optional.of(profileView("Alice")));
        when(oauthAccountRepository.findByUserId(userId)).thenReturn(List.of());

        ProfileResponse response = service.getProfile(userId);

        assertThat(response.displayName()).isEqualTo("Alice");
        UUID missing = UUID.randomUUID();
        when(userRepository.findProfileViewById(missing)).thenReturn(Optional.empty());
        assertError(() -> service.getProfile(missing), ErrorCode.USER_001);
    }

    @Test
    void updateProfileAppliesOnlyNonNullFieldsAndReturnsFreshView() {
        User user = new User();
        user.setDisplayName("Old");
        user.setBio("keep me");
        UUID wardId = UUID.randomUUID();
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(administrativeUnitRepository.findWardWithProvince(wardId))
                .thenReturn(Optional.of(projections.createProjection(WardView.class, Map.of("wardId", wardId))));
        when(userRepository.findProfileViewById(userId)).thenReturn(Optional.of(profileView("New")));
        when(oauthAccountRepository.findByUserId(userId)).thenReturn(List.of());

        ProfileResponse response = service.updateProfile(userId, new ProfileUpdateRequest(
                "New", null, null, "Tiền vệ", LocalDate.of(1995, 5, 15), "MALE", wardId));

        assertThat(user.getDisplayName()).isEqualTo("New");
        assertThat(user.getBio()).isEqualTo("keep me");
        assertThat(user.getPreferredPosition()).isEqualTo("Tiền vệ");
        assertThat(user.getDateOfBirth()).isEqualTo(LocalDate.of(1995, 5, 15));
        assertThat(user.getGender()).isEqualTo("MALE");
        assertThat(user.getWardId()).isEqualTo(wardId);
        assertThat(response.displayName()).isEqualTo("New");
        verify(userRepository).saveAndFlush(user);
    }

    @Test
    void updateProfileGuardsUser001ThenUser002() {
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.empty());
        ProfileUpdateRequest request = new ProfileUpdateRequest(null, null, null, null, null, null, UUID.randomUUID());
        assertError(() -> service.updateProfile(userId, request), ErrorCode.USER_001);

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(new User()));
        when(administrativeUnitRepository.findWardWithProvince(any())).thenReturn(Optional.empty());
        assertError(() -> service.updateProfile(userId, request), ErrorCode.USER_002);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void deleteAccountRejectsActiveCaptainWithoutSideEffects() {
        when(teamMemberRepository.existsActiveCaptainByUserId(userId)).thenReturn(true);

        assertError(() -> service.deleteAccount(userId), ErrorCode.USER_003);

        verify(teamMemberRepository, never()).leaveAllActive(any(), any());
        verify(teamMemberRepository, never()).rejectAllPending(any());
        verify(userRepository, never()).softDelete(any(), any());
    }

    @Test
    void deleteAccountLeavesRechecksCaptainRejectsAndSoftDeletesInOrder() {
        when(teamMemberRepository.existsActiveCaptainByUserId(userId)).thenReturn(false);

        service.deleteAccount(userId);

        InOrder order = inOrder(teamMemberRepository, userRepository);
        order.verify(teamMemberRepository).existsActiveCaptainByUserId(userId);
        order.verify(teamMemberRepository).leaveAllActive(eq(userId), any());
        order.verify(teamMemberRepository).existsActiveCaptainByUserId(userId);
        order.verify(teamMemberRepository).rejectAllPending(userId);
        order.verify(userRepository).softDelete(eq(userId), any());
    }

    @Test
    void deleteAccountThrowsUser003WhenBecameCaptainConcurrently() {
        // R1: transfer-cho-user commit giữa Q1 và Q2 → Q3 thấy row CAPTAIN ACTIVE.
        when(teamMemberRepository.existsActiveCaptainByUserId(userId)).thenReturn(false, true);

        assertError(() -> service.deleteAccount(userId), ErrorCode.USER_003);

        verify(teamMemberRepository).leaveAllActive(eq(userId), any());
        verify(teamMemberRepository, never()).rejectAllPending(any());
        verify(userRepository, never()).softDelete(any(), any());
    }
}
