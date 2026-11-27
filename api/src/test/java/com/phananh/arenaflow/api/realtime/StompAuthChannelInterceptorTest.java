package com.phananh.arenaflow.api.realtime;

import com.phananh.arenaflow.api.config.SecurityConfig;
import com.phananh.arenaflow.application.service.ConversationService;
import com.phananh.arenaflow.application.support.security.JwtProperties;
import com.phananh.arenaflow.application.support.security.JwtTokenService;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.entity.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.security.Principal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class StompAuthChannelInterceptorTest {

    private static final String SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="; // allow-secret: fixture giả chỉ dùng cho test

    private final JwtProperties properties = new JwtProperties(SECRET, Duration.ofHours(1), Duration.ofDays(7));
    private final JwtTokenService tokenService = new JwtTokenService(properties);
    private final JwtDecoder decoder = new SecurityConfig().jwtDecoder(properties);
    private final ConversationService conversationService = mock(ConversationService.class);
    private final MessageChannel channel = mock(MessageChannel.class);
    private final UUID userId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();

    private StompAuthChannelInterceptor interceptor(Clock clock) {
        return new StompAuthChannelInterceptor(decoder, conversationService, clock);
    }

    private Message<byte[]> frame(StompCommand command, Map<String, Object> session, String authorization,
                                  String destination, Principal user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionAttributes(session);
        if (authorization != null) {
            accessor.addNativeHeader("Authorization", authorization);
        }
        if (destination != null) {
            accessor.setDestination(destination);
        }
        accessor.setUser(user);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static void assertCode(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(StompRealtimeException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }

    private Principal connect(StompAuthChannelInterceptor interceptor, Map<String, Object> session) {
        Message<?> result = interceptor.preSend(frame(StompCommand.CONNECT, session,
                "Bearer " + tokenService.issueAccessToken(userId, User.Role.USER), null, null), channel);
        return StompHeaderAccessor.wrap(result).getUser();
    }

    @Test
    void connectWithAccessTokenSetsUserPrincipal() {
        Map<String, Object> session = new HashMap<>();

        Principal user = connect(interceptor(Clock.systemDefaultZone()), session);

        assertThat(user.getName()).isEqualTo(userId.toString());
        assertThat(session).containsKey(StompAuthChannelInterceptor.EXPIRES_AT);
    }

    @Test
    void connectWithMissingWrongOrRefreshTokenIsAuth001() {
        StompAuthChannelInterceptor interceptor = interceptor(Clock.systemDefaultZone());
        assertCode(() -> interceptor.preSend(frame(StompCommand.CONNECT, new HashMap<>(), null, null, null), channel),
                ErrorCode.AUTH_001);
        assertCode(() -> interceptor.preSend(frame(StompCommand.CONNECT, new HashMap<>(), "Bearer abc", null, null),
                channel), ErrorCode.AUTH_001);
        String refresh = tokenService.issueRefreshToken(userId);
        assertCode(() -> interceptor.preSend(frame(StompCommand.CONNECT, new HashMap<>(), "Bearer " + refresh, null,
                null), channel), ErrorCode.AUTH_001);
    }

    @Test
    void subscribeToOwnConversationChecksParticipation() {
        StompAuthChannelInterceptor interceptor = interceptor(Clock.systemDefaultZone());
        Map<String, Object> session = new HashMap<>();
        Principal user = connect(interceptor, session);

        interceptor.preSend(frame(StompCommand.SUBSCRIBE, session, null, "/topic/conversations/" + conversationId,
                user), channel);

        verify(conversationService).requireSubscribable(conversationId, userId);
    }

    @Test
    void subscribeByOutsiderCarriesChat003() {
        StompAuthChannelInterceptor interceptor = interceptor(Clock.systemDefaultZone());
        Map<String, Object> session = new HashMap<>();
        Principal user = connect(interceptor, session);
        doThrow(new BusinessException(ErrorCode.CHAT_003)).when(conversationService)
                .requireSubscribable(conversationId, userId);

        assertCode(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, session, null,
                "/topic/conversations/" + conversationId, user), channel), ErrorCode.CHAT_003);
    }

    @Test
    void subscribeRejectsWildcardsOtherTopicsAndQueues() {
        StompAuthChannelInterceptor interceptor = interceptor(Clock.systemDefaultZone());
        Map<String, Object> session = new HashMap<>();
        Principal user = connect(interceptor, session);

        for (String destination : new String[]{"/topic/**", "/topic/conversations/*",
                "/topic/pairings/" + conversationId, "/queue/x", "/user/queue/notifications",
                "/topic/conversations/" + conversationId + "/x", "/topic/conversations/not-a-uuid"}) {
            assertCode(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, session, null, destination, user),
                    channel), ErrorCode.SYS_004);
        }
        verifyNoInteractions(conversationService);
    }

    @Test
    void subscribeAfterTokenExpiryIsAuth001() {
        Map<String, Object> session = new HashMap<>();
        Principal user = connect(interceptor(Clock.systemDefaultZone()), session);
        Clock later = Clock.fixed(Instant.now().plus(Duration.ofHours(2)), ZoneId.systemDefault());

        assertCode(() -> interceptor(later).preSend(frame(StompCommand.SUBSCRIBE, session, null,
                "/topic/conversations/" + conversationId, user), channel), ErrorCode.AUTH_001);
        verifyNoInteractions(conversationService);
    }

    @Test
    void subscribeWithoutConnectedUserIsAuth001() {
        assertCode(() -> interceptor(Clock.systemDefaultZone()).preSend(frame(StompCommand.SUBSCRIBE, new HashMap<>(), null,
                "/topic/conversations/" + conversationId, null), channel), ErrorCode.AUTH_001);
    }

    @Test
    void clientSendIsRejected() {
        assertCode(() -> interceptor(Clock.systemDefaultZone()).preSend(frame(StompCommand.SEND, new HashMap<>(), null,
                "/topic/conversations/" + conversationId, null), channel), ErrorCode.SYS_002);
    }
}
