package com.phananh.ArenaFlow.service;

import com.phananh.ArenaFlow.config.JwtProperties;
import com.phananh.ArenaFlow.dto.request.OAuthLoginRequest;
import com.phananh.ArenaFlow.dto.request.RefreshTokenRequest;
import com.phananh.ArenaFlow.dto.request.SendOtpRequest;
import com.phananh.ArenaFlow.dto.request.VerifyOtpRequest;
import com.phananh.ArenaFlow.dto.response.AuthResponse;
import com.phananh.ArenaFlow.entity.user.OAuthAccount;
import com.phananh.ArenaFlow.entity.user.User;
import com.phananh.ArenaFlow.exception.BusinessException;
import com.phananh.ArenaFlow.exception.ErrorCode;
import com.phananh.ArenaFlow.repository.OAuthAccountRepository;
import com.phananh.ArenaFlow.repository.UserRepository;
import com.phananh.ArenaFlow.repository.projection.UserAuthView;
import com.phananh.ArenaFlow.security.JwtTokenService;
import com.phananh.ArenaFlow.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceImplTest {

    private static final String SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="; // allow-secret: fixture giả chỉ dùng cho test

    private final UserRepository userRepository = mock(UserRepository.class);
    private final OAuthAccountRepository oauthAccountRepository = mock(OAuthAccountRepository.class);
    private final JwtProperties jwtProperties = new JwtProperties(SECRET, Duration.ofHours(1), Duration.ofDays(7));
    private final JwtTokenService jwtTokenService = new JwtTokenService(jwtProperties);
    private final TransactionTemplate transactionTemplate =
            new TransactionTemplate(mock(PlatformTransactionManager.class));
    private final SpelAwareProxyProjectionFactory projections = new SpelAwareProxyProjectionFactory();

    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(userRepository, oauthAccountRepository, jwtTokenService, jwtProperties,
                transactionTemplate);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", UUID.fromString("0190a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b"));
            return user;
        });
    }

    private UserAuthView authView(UUID userId, String role, boolean deleted) {
        return projections.createProjection(UserAuthView.class,
                Map.of("userId", userId, "role", role, "deleted", deleted));
    }

    @Test
    void oauthLoginReturnsExistingActiveUserWithoutCreating() {
        UUID userId = UUID.randomUUID();
        when(oauthAccountRepository.findAuthView(OAuthAccount.Provider.google, "a@example.com"))
                .thenReturn(Optional.of(authView(userId, "ADMIN", false)));

        AuthResult result = service.loginWithOAuth(new OAuthLoginRequest("GOOGLE", "token", "a@example.com"));

        assertThat(result.isNewUser()).isFalse();
        AuthResponse body = result.body();
        assertThat(body.userId()).isEqualTo(userId);
        assertThat(body.role()).isEqualTo("ADMIN");
        assertThat(body.isNewUser()).isFalse();
        assertThat(body.tokenType()).isEqualTo("Bearer");
        assertThat(body.expiresIn()).isEqualTo(3600);
        assertThat(jwtTokenService.parseRefreshToken(body.refreshToken())).isEqualTo(userId);
        verify(userRepository, never()).save(any());
    }

    @Test
    void oauthLoginRejectsDeletedAccountWithAuth003() {
        when(oauthAccountRepository.findAuthView(OAuthAccount.Provider.facebook, "a@example.com"))
                .thenReturn(Optional.of(authView(UUID.randomUUID(), "USER", true)));

        assertThatThrownBy(() -> service.loginWithOAuth(new OAuthLoginRequest("FACEBOOK", "t", "a@example.com")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_003));
    }

    @Test
    void oauthLoginRegistersNewUserWithDefaultDisplayName() {
        when(oauthAccountRepository.findAuthView(any(), anyString())).thenReturn(Optional.empty());

        AuthResult result = service.loginWithOAuth(new OAuthLoginRequest("GOOGLE", "token", "new@example.com"));

        assertThat(result.isNewUser()).isTrue();
        assertThat(result.body().isNewUser()).isTrue();
        assertThat(result.body().role()).isEqualTo("USER");
        ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(user.capture());
        assertThat(user.getValue().getDisplayName()).isEqualTo("User 3f4a5b");
        ArgumentCaptor<OAuthAccount> account = ArgumentCaptor.forClass(OAuthAccount.class);
        verify(oauthAccountRepository).saveAndFlush(account.capture());
        assertThat(account.getValue().getProvider()).isEqualTo(OAuthAccount.Provider.google);
        assertThat(account.getValue().getProviderUserId()).isEqualTo("new@example.com");
        assertThat(account.getValue().getUserId()).isEqualTo(result.body().userId());
    }

    @Test
    void oauthLoginReReadsExistingAccountWhenConcurrentRegistrationWins() {
        UUID winner = UUID.randomUUID();
        when(oauthAccountRepository.findAuthView(OAuthAccount.Provider.google, "race@example.com"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(authView(winner, "USER", false)));
        when(oauthAccountRepository.saveAndFlush(any(OAuthAccount.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        AuthResult result = service.loginWithOAuth(new OAuthLoginRequest("GOOGLE", "t", "race@example.com"));

        assertThat(result.isNewUser()).isFalse();
        assertThat(result.body().userId()).isEqualTo(winner);
    }

    @Test
    void verifyOtpRegistersPhoneUserWithPhoneNumber() {
        when(oauthAccountRepository.findAuthView(eq(OAuthAccount.Provider.phone), eq("0987654321")))
                .thenReturn(Optional.empty());

        AuthResult result = service.verifyOtp(new VerifyOtpRequest("0987654321", "123456"));

        assertThat(result.isNewUser()).isTrue();
        ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(user.capture());
        assertThat(user.getValue().getPhoneNumber()).isEqualTo("0987654321");
        ArgumentCaptor<OAuthAccount> account = ArgumentCaptor.forClass(OAuthAccount.class);
        verify(oauthAccountRepository).saveAndFlush(account.capture());
        assertThat(account.getValue().getProvider()).isEqualTo(OAuthAccount.Provider.phone);
    }

    @Test
    void sendOtpIsMockedWithFiveMinuteExpiry() {
        assertThat(service.sendOtp(new SendOtpRequest("0987654321")).expiresIn()).isEqualTo(300);
    }

    @Test
    void refreshIssuesNewPairWithCurrentRole() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        ReflectionTestUtils.setField(user, "id", userId);
        user.setRole(User.Role.ADMIN);
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));

        AuthResponse response = service.refresh(new RefreshTokenRequest(jwtTokenService.issueRefreshToken(userId)));

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.role()).isEqualTo("ADMIN");
        assertThat(response.isNewUser()).isFalse();
    }

    @Test
    void refreshRejectsInvalidTokenAndDeletedUser() {
        assertThatThrownBy(() -> service.refresh(new RefreshTokenRequest("garbage")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_002));

        UUID deletedUser = UUID.randomUUID();
        when(userRepository.findByIdAndDeletedAtIsNull(deletedUser)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.refresh(new RefreshTokenRequest(jwtTokenService.issueRefreshToken(deletedUser))))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_003));
    }
}
