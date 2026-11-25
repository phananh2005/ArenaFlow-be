package com.phananh.arenaflow.api.realtime;

import com.phananh.arenaflow.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

import java.nio.charset.StandardCharsets;

/**
 * Frame ERROR: header {@code message} = mã lỗi, body {@code {code, message}}. NEVER đưa token / stacktrace / nội dung
 * frame của client vào frame lỗi hay log; lỗi không rõ → SYS_005.
 */
@Slf4j
public class StompErrorHandler extends StompSubProtocolErrorHandler {

    @Override
    public Message<byte[]> handleClientMessageProcessingError(Message<byte[]> clientMessage, Throwable exception) {
        ErrorCode code = errorCode(exception);
        log.warn("STOMP client frame rejected: {}", code.getCode());
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
        accessor.setMessage(code.getCode());
        accessor.setLeaveMutable(true);
        String body = "{\"code\":\"" + code.getCode() + "\",\"message\":\"" + code.getMessage() + "\"}";
        return MessageBuilder.createMessage(body.getBytes(StandardCharsets.UTF_8), accessor.getMessageHeaders());
    }

    private static ErrorCode errorCode(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof StompRealtimeException realtime) {
                return realtime.getErrorCode();
            }
        }
        return ErrorCode.SYS_005;
    }
}
