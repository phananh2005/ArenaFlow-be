package com.phananh.arenaflow.common.exception;

/**
 * Vượt giới hạn số lần gọi: trả status/mã của {@code errorCode} kèm header {@code Retry-After} =
 * {@code retryAfterSeconds} (số giây nguyên, tối thiểu 1, tới khi gọi lại được).
 */
public class RateLimitExceededException extends BusinessException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(ErrorCode errorCode, long retryAfterSeconds) {
        super(errorCode);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
