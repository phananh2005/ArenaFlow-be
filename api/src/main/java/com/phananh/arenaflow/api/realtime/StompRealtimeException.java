package com.phananh.arenaflow.api.realtime;

import com.phananh.arenaflow.common.exception.ErrorCode;
import org.springframework.messaging.MessagingException;

/** Lỗi xử lý frame STOMP của client; bộ xử lý lỗi đổi thành frame ERROR mang mã lỗi (không token, không stacktrace). */
public class StompRealtimeException extends MessagingException {

    private final ErrorCode errorCode;

    public StompRealtimeException(ErrorCode errorCode) {
        super(errorCode.getCode());
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
