package com.phananh.ArenaFlow.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeTest {

    @Test
    void mapsAuthenticationAndTeamErrorsToTheirHttpStatuses() {
        assertThat(ErrorCode.AUTH_001.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ErrorCode.AUTH_004.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ErrorCode.TEAM_001.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorCode.TEAM_014.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.TEAM_015.getHttpStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void exposesTheApiCodeAndMessage() {
        assertThat(ErrorCode.USER_001.getCode()).isEqualTo("USER_001");
        assertThat(ErrorCode.USER_001.getMessage()).isEqualTo("Không tìm thấy người dùng");
    }
}
