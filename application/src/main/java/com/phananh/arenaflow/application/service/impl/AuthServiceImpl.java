package com.phananh.arenaflow.application.service.impl;

import com.phananh.arenaflow.application.support.security.JwtProperties;
import com.phananh.arenaflow.application.dto.request.OAuthLoginRequest;
import com.phananh.arenaflow.application.dto.request.RefreshTokenRequest;
import com.phananh.arenaflow.application.dto.request.SendOtpRequest;
import com.phananh.arenaflow.application.dto.request.VerifyOtpRequest;
import com.phananh.arenaflow.application.dto.response.AuthResponse;
import com.phananh.arenaflow.application.dto.response.OtpSentResponse;
import com.phananh.arenaflow.domain.entity.user.OAuthAccount;
import com.phananh.arenaflow.domain.entity.user.User;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.repository.OAuthAccountRepository;
import com.phananh.arenaflow.domain.repository.UserRepository;
import com.phananh.arenaflow.domain.projection.UserAuthView;
import com.phananh.arenaflow.application.support.security.JwtTokenService;
import com.phananh.arenaflow.application.service.AuthResult;
import com.phananh.arenaflow.application.service.AuthService;
import org.springframework.dao.DataIntegrityViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Locale;
import java.util.UUID;

/**
 * Đăng ký-hoặc-đăng nhập (OAuth/OTP mock) và refresh token.
 * Tạo tài khoản chạy trong transaction riêng (TransactionTemplate) để khi đụng unique (đăng ký song song)
 * transaction đó rollback và việc đọc lại tài khoản thắng chạy ngoài transaction đã hỏng.
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final int OTP_EXPIRES_IN_SECONDS = 300;
    private static final String TOKEN_TYPE = "Bearer";
    private static final String DEFAULT_NAME_PREFIX = "User ";
    private static final int DEFAULT_NAME_SUFFIX_LENGTH = 6;

    private final UserRepository userRepository;
    private final OAuthAccountRepository oauthAccountRepository;
    private final JwtTokenService jwtTokenService;
    private final JwtProperties jwtProperties;
    private final TransactionTemplate transactionTemplate;

    @Override
    public AuthResult loginWithOAuth(OAuthLoginRequest request) {
        OAuthAccount.Provider provider = OAuthAccount.Provider.valueOf(request.provider().toLowerCase(Locale.ROOT));
        // Email là khoá nhận diện tài khoản (provider_user_id): chữ thường để khác hoa/thường không tạo tài khoản mới.
        // trim() là lớp phòng hờ — qua API, @Email đã từ chối khoảng trắng đầu/cuối.
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        return registerOrLogin(provider, email, null);
    }

    @Override
    public OtpSentResponse sendOtp(SendOtpRequest request) {
        return new OtpSentResponse(request.phone(), OTP_EXPIRES_IN_SECONDS);
    }

    @Override
    public AuthResult verifyOtp(VerifyOtpRequest request) {
        return registerOrLogin(OAuthAccount.Provider.phone, request.phone(), request.phone());
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse refresh(RefreshTokenRequest request) {
        UUID userId = jwtTokenService.parseRefreshToken(request.refreshToken());
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_003));
        return issueTokens(userId, user.getRole(), false);
    }

    /** Dùng provider + providerUserId làm khoá nhận diện; không có liên kết thì tạo user mới. */
    private AuthResult registerOrLogin(OAuthAccount.Provider provider, String providerUserId, String phoneNumber) {
        return oauthAccountRepository.findAuthView(provider, providerUserId)
                .map(this::loginExisting)
                .orElseGet(() -> register(provider, providerUserId, phoneNumber));
    }

    /** User đã xoá bị từ chối bằng AUTH_003; ngược lại phát token theo role hiện tại. */
    private AuthResult loginExisting(UserAuthView account) {
        if (Boolean.TRUE.equals(account.getDeleted())) {
            throw new BusinessException(ErrorCode.AUTH_003);
        }
        AuthResponse body = issueTokens(account.getUserId(), User.Role.valueOf(account.getRole()), false);
        return new AuthResult(body, false);
    }

    /** Đăng ký trong transaction riêng; unique conflict do request song song → đọc lại tài khoản thắng. */
    private AuthResult register(OAuthAccount.Provider provider, String providerUserId, String phoneNumber) {
        try {
            UUID userId = transactionTemplate.execute(status -> createAccount(provider, providerUserId, phoneNumber));
            return new AuthResult(issueTokens(userId, User.Role.USER, true), true);
        } catch (DataIntegrityViolationException concurrentRegistration) {
            // Request song song đã tạo cùng (provider, provider_user_id) → đọc lại, đăng nhập (200).
            return oauthAccountRepository.findAuthView(provider, providerUserId)
                    .map(this::loginExisting)
                    .orElseThrow(() -> concurrentRegistration);
        }
    }

    /** Tạo user + liên kết OAuth; dựng tên mặc định có hậu tố từ UUID sau khi user được persist. */
    private UUID createAccount(OAuthAccount.Provider provider, String providerUserId, String phoneNumber) {
        User user = new User();
        user.setDisplayName(DEFAULT_NAME_PREFIX.trim());
        user.setPhoneNumber(phoneNumber);
        User saved = userRepository.save(user);
        // id chỉ có sau persist → tên mặc định "User " + 6 ký tự cuối userId.
        String id = saved.getId().toString();
        saved.setDisplayName(DEFAULT_NAME_PREFIX + id.substring(id.length() - DEFAULT_NAME_SUFFIX_LENGTH));

        OAuthAccount account = new OAuthAccount();
        account.setUserId(saved.getId());
        account.setProvider(provider);
        account.setProviderUserId(providerUserId);
        oauthAccountRepository.saveAndFlush(account);
        return saved.getId();
    }

    /** Phát access/refresh token cùng role hiện tại và TTL access token tính bằng giây. */
    private AuthResponse issueTokens(UUID userId, User.Role role, boolean isNewUser) {
        return new AuthResponse(userId, role.name(), isNewUser,
                jwtTokenService.issueAccessToken(userId, role),
                jwtTokenService.issueRefreshToken(userId),
                TOKEN_TYPE, jwtProperties.accessTokenTtl().toSeconds());
    }
}
