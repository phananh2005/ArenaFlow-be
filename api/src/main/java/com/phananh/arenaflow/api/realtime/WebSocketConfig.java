package com.phananh.arenaflow.api.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.config.SimpleBrokerRegistration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket tại {@code /ws} (có SockJS dự phòng), simple broker trong bộ nhớ cho {@code /topic}. Client
 * không SEND (không đặt tiền tố đích ứng dụng); lọc frame theo header {@code selector} bị tắt (giá trị là biểu thức do
 * client gửi — giảm bề mặt tấn công). Xác thực / phân quyền ở {@link StompAuthChannelInterceptor}.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketProperties properties;
    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(properties.allowedOriginPatterns().toArray(String[]::new))
                .withSockJS();
        registry.setErrorHandler(new StompErrorHandler());
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        long heartbeat = properties.heartbeatMs();
        SimpleBrokerRegistration broker = registry.enableSimpleBroker("/topic")
                .setHeartbeatValue(new long[]{heartbeat, heartbeat})
                .setTaskScheduler(wsHeartbeatScheduler());
        broker.setSelectorHeaderName(null);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }

    /** Một luồng riêng cho heartbeat, không chiếm pool của job định kỳ. */
    @Bean
    public ThreadPoolTaskScheduler wsHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        return scheduler;
    }
}
