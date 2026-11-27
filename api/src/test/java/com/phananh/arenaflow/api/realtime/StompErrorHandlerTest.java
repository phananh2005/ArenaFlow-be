package com.phananh.arenaflow.api.realtime;

import com.phananh.arenaflow.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class StompErrorHandlerTest {

    private final StompErrorHandler handler = new StompErrorHandler();

    private Message<byte[]> clientFrame() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void errorFrameCarriesOnlyErrorCodeAndMessage() {
        MessageDeliveryException wrapped = new MessageDeliveryException(clientFrame(), "wrapped",
                new StompRealtimeException(ErrorCode.CHAT_003));

        Message<byte[]> error = handler.handleClientMessageProcessingError(clientFrame(), wrapped);

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(error);
        assertThat(accessor.getCommand()).isEqualTo(StompCommand.ERROR);
        assertThat(accessor.getMessage()).isEqualTo("CHAT_003");
        String body = new String(error.getPayload(), StandardCharsets.UTF_8);
        assertThat(body).contains("\"code\":\"CHAT_003\"").contains(ErrorCode.CHAT_003.getMessage())
                .doesNotContain("Exception").doesNotContain("at com.");
    }

    @Test
    void unknownFailureIsSystemErrorWithoutDetails() {
        Message<byte[]> error = handler.handleClientMessageProcessingError(clientFrame(),
                new IllegalStateException("token=secret-value"));

        assertThat(StompHeaderAccessor.wrap(error).getMessage()).isEqualTo("SYS_005");
        assertThat(new String(error.getPayload(), StandardCharsets.UTF_8)).doesNotContain("secret-value");
    }
}
