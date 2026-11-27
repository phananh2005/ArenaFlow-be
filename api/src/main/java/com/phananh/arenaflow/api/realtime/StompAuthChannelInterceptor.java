package com.phananh.arenaflow.api.realtime;

import com.phananh.arenaflow.application.service.ConversationService;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Xác thực và phân quyền kênh STOMP. CONNECT: access token ở native header {@code Authorization} (cùng decoder REST,
 * refresh token bị từ chối) → principal = userId, lưu hạn token vào phiên. SUBSCRIBE: chỉ đúng mẫu
 * {@code /topic/conversations/{UUID}} (không wildcard / topic khác / queue) → token còn hạn → người gọi tham gia cuộc
 * trò chuyện. SEND bị từ chối (client gửi tin qua REST). Lỗi ném {@link StompRealtimeException} → frame ERROR.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    static final String EXPIRES_AT = "arenaflow.tokenExpiresAt";

    private static final String BEARER_PREFIX = "Bearer ";
    private static final Pattern CONVERSATION_TOPIC = Pattern.compile(
            "^/topic/conversations/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$");

    private final JwtDecoder jwtDecoder;
    private final ConversationService conversationService;
    private final Clock clock;

    /**
     * {@code @Lazy} cho dịch vụ chat: dịch vụ chat cần gateway → SimpMessagingTemplate, mà template lại được dựng từ
     * cấu hình STOMP đang cần interceptor này — proxy lười cắt vòng phụ thuộc lúc khởi động.
     */
    public StompAuthChannelInterceptor(JwtDecoder jwtDecoder, @Lazy ConversationService conversationService,
                                       Clock clock) {
        this.jwtDecoder = jwtDecoder;
        this.conversationService = conversationService;
        this.clock = clock;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        switch (accessor.getCommand()) {
            case CONNECT, STOMP -> authenticate(accessor);
            case SUBSCRIBE -> authorizeSubscribe(accessor);
            case SEND -> throw new StompRealtimeException(ErrorCode.SYS_002);
            default -> {
                // Các lệnh khác (UNSUBSCRIBE, DISCONNECT, ACK…) không cần kiểm.
            }
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader("Authorization");
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new StompRealtimeException(ErrorCode.AUTH_001);
        }
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(authorization.substring(BEARER_PREFIX.length()).trim());
        } catch (JwtException exception) {
            throw new StompRealtimeException(ErrorCode.AUTH_001);
        }
        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new StompRealtimeException(ErrorCode.AUTH_001);
        }
        accessor.setUser(new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of()));
        Map<String, Object> session = accessor.getSessionAttributes();
        if (session != null) {
            session.put(EXPIRES_AT, jwt.getExpiresAt());
        }
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        Matcher matcher = destination == null ? null : CONVERSATION_TOPIC.matcher(destination);
        if (matcher == null || !matcher.matches()) {
            throw new StompRealtimeException(ErrorCode.SYS_004);
        }
        Principal user = accessor.getUser();
        Map<String, Object> session = accessor.getSessionAttributes();
        Object expiresAt = session == null ? null : session.get(EXPIRES_AT);
        if (user == null || !(expiresAt instanceof Instant expiry) || !expiry.isAfter(clock.instant())) {
            throw new StompRealtimeException(ErrorCode.AUTH_001);
        }
        try {
            conversationService.requireSubscribable(UUID.fromString(matcher.group(1)), UUID.fromString(user.getName()));
        } catch (BusinessException exception) {
            throw new StompRealtimeException(exception.getErrorCode());
        }
    }
}
