package com.phananh.ArenaFlow.service;

import com.phananh.ArenaFlow.common.OptimisticLockRetrier;
import com.phananh.ArenaFlow.dto.request.ProfileUpdateRequest;
import com.phananh.ArenaFlow.dto.response.ProfileResponse;
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
import com.phananh.ArenaFlow.repository.projection.WardView;
import com.phananh.ArenaFlow.service.impl.ProfileServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfileServiceImplTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final OAuthAccountRepository oauthAccountRepository = mock(OAuthAccountRepository.class);
    private final AdministrativeUnitRepository administrativeUnitRepository = mock(AdministrativeUnitRepository.class);
    private final TeamRepository teamRepository = mock(TeamRepository.class);
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
                teamRepository, teamMemberRepository, retrier, profileMapper);
        when(retrier.execute(any())).thenAnswer(invocation -> ((Supplier<Object>) invocation.getArgument(0)).get());
    }

    private ProfileView profileView(String displayName) {
        return projections.createProjection(ProfileView.class,
                Map.of("userId", userId, "role", "USER", "displayName", displayName));
    }

    private CaptainTeamView captainTeam(UUID teamId, long activeCount) {
        return projections.createProjection(CaptainTeamView.class, Map.of("teamId", teamId, "activeCount", activeCount));
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
    void deleteAccountRejectsCaptainOfTeamWithOtherActiveMembers() {
        when(teamMemberRepository.findCaptainTeamsWithActiveCount(userId))
                .thenReturn(List.of(captainTeam(UUID.randomUUID(), 2)));

        assertError(() -> service.deleteAccount(userId), ErrorCode.USER_003);
        verify(userRepository, never()).softDelete(any(), any());
    }

    @Test
    void deleteAccountMarksSoloTeamsInactiveThenLeavesRejectsAndSoftDeletes() {
        UUID soloTeam = UUID.randomUUID();
        when(teamMemberRepository.findCaptainTeamsWithActiveCount(userId)).thenReturn(List.of(captainTeam(soloTeam, 1)));
        when(teamRepository.markInactiveByIds(eq(Set.of(soloTeam)), eq("Đội trưởng duy nhất đã xoá tài khoản"), any()))
                .thenReturn(1);

        service.deleteAccount(userId);

        verify(teamMemberRepository).leaveAllActive(eq(userId), any());
        verify(teamMemberRepository).rejectAllPending(userId);
        verify(userRepository).softDelete(eq(userId), any());
    }

    @Test
    void deleteAccountSignalsRetryWhenSomeoneJoinedSoloTeamConcurrently() {
        UUID soloTeam = UUID.randomUUID();
        when(teamMemberRepository.findCaptainTeamsWithActiveCount(userId)).thenReturn(List.of(captainTeam(soloTeam, 1)));
        when(teamRepository.markInactiveByIds(anyCollection(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.deleteAccount(userId))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        verify(userRepository, never()).softDelete(any(), any());
    }

    @Test
    void deleteAccountWithoutCaptainTeamsSkipsMarkInactive() {
        when(teamMemberRepository.findCaptainTeamsWithActiveCount(userId)).thenReturn(List.of());

        service.deleteAccount(userId);

        verify(teamRepository, never()).markInactiveByIds(anyCollection(), any(), any());
        verify(userRepository).softDelete(eq(userId), any());
    }
}
