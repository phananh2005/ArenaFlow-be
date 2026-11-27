package com.phananh.arenaflow.common.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitExceededExceptionTest {

    @Test
    void isBusinessExceptionCarryingRetryAfterSeconds() {
        RateLimitExceededException exception = new RateLimitExceededException(ErrorCode.USER_007, 42);

        assertThat(exception).isInstanceOf(BusinessException.class);
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_007);
        assertThat(exception.getDetails()).isNull();
        assertThat(exception.getRetryAfterSeconds()).isEqualTo(42);
        assertThat(exception.getMessage()).isEqualTo("Bạn tìm quá nhanh, vui lòng thử lại sau");
    }

    @Test
    void userDiscoveryErrorCodesHaveExpectedStatusAndMessage() {
        assertThat(ErrorCode.USER_004.getHttpStatus().value()).isEqualTo(404);
        assertThat(ErrorCode.USER_004.getMessage()).isEqualTo("Không tìm thấy tỉnh/thành");
        assertThat(ErrorCode.USER_005.getHttpStatus().value()).isEqualTo(400);
        assertThat(ErrorCode.USER_005.getMessage()).isEqualTo("Số điện thoại không hợp lệ");
        assertThat(ErrorCode.USER_006.getHttpStatus().value()).isEqualTo(400);
        assertThat(ErrorCode.USER_006.getMessage()).isEqualTo("Nhập ít nhất 2 ký tự");
        assertThat(ErrorCode.USER_007.getHttpStatus().value()).isEqualTo(429);
        assertThat(ErrorCode.USER_007.getCode()).isEqualTo("USER_007");
    }
}
