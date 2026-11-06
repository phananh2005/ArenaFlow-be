package com.phananh.arenaflow.common.exception;

public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String details;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    /** {@code details} (có thể null) trả nguyên ở field {@code details} của envelope lỗi; message lấy từ errorCode. */
    public BusinessException(ErrorCode errorCode, String details) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.details = details;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public String getDetails() {
        return details;
    }
}
