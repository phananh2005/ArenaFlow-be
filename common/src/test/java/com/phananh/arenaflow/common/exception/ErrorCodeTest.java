package com.phananh.arenaflow.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/** Kiểm mã lỗi khớp HTTP status, định dạng mã và thông báo mặc định. */
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
    void venueContactFieldsAreAdminOnlyAndPublicListIsActiveOnly() {
        assertThat(ErrorCode.VENUE_017.getCode()).isEqualTo("VENUE_017");
        assertThat(ErrorCode.VENUE_017.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ErrorCode.VENUE_017.getMessage())
                .isEqualTo("Chỉ quản trị viên được nhập số điện thoại và website của địa điểm");
        assertThat(ErrorCode.VENUE_018.getCode()).isEqualTo("VENUE_018");
        assertThat(ErrorCode.VENUE_018.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ErrorCode.VENUE_018.getMessage())
                .isEqualTo("Danh sách này chỉ gồm địa điểm đang hoạt động; quản trị viên dùng danh sách quản trị");
    }

    @Test
    void matchSportAndChatErrorsUseContractStatusesAndMessages() {
        assertThat(ErrorCode.MATCH_001.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorCode.MATCH_002.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorCode.MATCH_003.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.MATCH_004.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.MATCH_005.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.MATCH_006.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.MATCH_007.getHttpStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ErrorCode.MATCH_008.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.MATCH_009.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ErrorCode.MATCH_010.getHttpStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ErrorCode.MATCH_011.getHttpStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ErrorCode.MATCH_012.getHttpStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ErrorCode.MATCH_013.getHttpStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ErrorCode.MATCH_014.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.MATCH_015.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.MATCH_016.getHttpStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ErrorCode.SPORT_001.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorCode.SPORT_002.getHttpStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ErrorCode.CHAT_001.getHttpStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ErrorCode.CHAT_002.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorCode.CHAT_003.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ErrorCode.CHAT_004.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.MATCH_004.getMessage()).isEqualTo("Kèo đã hết hạn");
        assertThat(ErrorCode.MATCH_016.getMessage()).isEqualTo("Thao tác không áp dụng cho loại kèo này");
        assertThat(ErrorCode.CHAT_004.getMessage()).isEqualTo("Cuộc trò chuyện chỉ còn xem");
    }

    @Test
    void pairingErrorsHaveContractStatusesAndMessages() {
        assertPairing(ErrorCode.PAIRING_001, HttpStatus.NOT_FOUND, "Không tìm thấy thương lượng ghép kèo");
        assertPairing(ErrorCode.PAIRING_002, HttpStatus.FORBIDDEN, "Đội không tham gia thương lượng này");
        assertPairing(ErrorCode.PAIRING_003, HttpStatus.CONFLICT, "Đội đã nhận kèo này");
        assertPairing(ErrorCode.PAIRING_004, HttpStatus.UNPROCESSABLE_ENTITY, "Không thể nhận kèo của đội mình");
        assertPairing(ErrorCode.PAIRING_005, HttpStatus.CONFLICT, "Thương lượng đã kết thúc");
        assertPairing(ErrorCode.PAIRING_006, HttpStatus.CONFLICT, "Phương án đã thay đổi, vui lòng tải lại");
        assertPairing(ErrorCode.PAIRING_007, HttpStatus.UNPROCESSABLE_ENTITY, "Chưa chốt địa điểm và giờ");
        assertPairing(ErrorCode.PAIRING_008, HttpStatus.UNPROCESSABLE_ENTITY,
                "Địa điểm hoặc giờ không thuộc lựa chọn của kèo");
    }

    private static void assertPairing(ErrorCode errorCode, HttpStatus status, String message) {
        assertThat(errorCode.getCode()).isEqualTo(errorCode.name());
        assertThat(errorCode.getHttpStatus()).isEqualTo(status);
        assertThat(errorCode.getMessage()).isEqualTo(message);
    }

    @Test
    void ratingReportAndDisputeErrorsUseApiStatusAndMessage() {
        assertThat(ErrorCode.RATING_001.getHttpStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ErrorCode.RATING_002.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.RATING_003.getMessage())
                .isEqualTo("Bạn là đội trưởng của cả hai đội, không thể đánh giá trận này");
        assertThat(ErrorCode.REPORT_001.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorCode.REPORT_004.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.REPORT_004.getMessage()).isEqualTo("Bạn đã report đội này với loại này trong trận");
        assertThat(ErrorCode.REPORT_007.getMessage()).isEqualTo("Đã quá hạn 24 giờ để tranh chấp");
        assertThat(ErrorCode.REPORT_008.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.REPORT_009.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ErrorCode.REPORT_010.getHttpStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ErrorCode.REPORT_011.getMessage()).isEqualTo("Cuộc trò chuyện này không report theo cách này");
        assertThat(ErrorCode.DISPUTE_001.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorCode.DISPUTE_002.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.DISPUTE_002.getMessage()).isEqualTo("Tranh chấp đã được xử lý");
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
