package com.phananh.ArenaFlow.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessExceptionTest {

    @Test
    void retainsErrorCodeAndOptionalDetails() {
        BusinessException exception = new BusinessException(ErrorCode.USER_001, "userId=123");

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_001);
        assertThat(exception.getDetails()).isEqualTo("userId=123");
        assertThat(exception.getMessage()).isEqualTo(ErrorCode.USER_001.getMessage());
    }
}
