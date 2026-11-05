package com.phananh.arenaflow.common.exception;

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
    void systemErrorsFollowDomainNumberFormat() {
        assertThat(ErrorCode.SYS_001.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorCode.SYS_002.getHttpStatus()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(ErrorCode.SYS_003.getHttpStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(ErrorCode.SYS_004.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ErrorCode.SYS_005.getHttpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(ErrorCode.values()).extracting(ErrorCode::getCode)
                .allMatch(code -> code.equals("VALIDATION_ERROR") || code.matches("[A-Z]+_\\d{3}"));
    }

    @Test
    void exposesTheApiCodeAndMessage() {
        assertThat(ErrorCode.USER_001.getCode()).isEqualTo("USER_001");
        assertThat(ErrorCode.USER_001.getMessage()).isEqualTo("Không tìm thấy người dùng");
    }

    @Test
    void disbandedTeamAndCaptainAccountDeletionErrors() {
        assertThat(ErrorCode.TEAM_016.getCode()).isEqualTo("TEAM_016");
        assertThat(ErrorCode.TEAM_016.getHttpStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ErrorCode.TEAM_016.getMessage()).isEqualTo("Đội đã giải tán");
        assertThat(ErrorCode.USER_003.getHttpStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ErrorCode.USER_003.getMessage())
                .isEqualTo("Bạn phải chuyển quyền đội trưởng hoặc giải tán đội trước khi xóa tài khoản");
    }
}
