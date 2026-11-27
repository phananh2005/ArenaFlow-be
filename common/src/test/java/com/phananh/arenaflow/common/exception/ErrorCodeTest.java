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
    void mercenaryErrorsMatchApiContract() {
        assertMercenary(ErrorCode.MERCENARY_001, HttpStatus.NOT_FOUND, "Không tìm thấy tin tuyển");
        assertMercenary(ErrorCode.MERCENARY_002, HttpStatus.NOT_FOUND, "Không tìm thấy đơn ứng tuyển");
        assertMercenary(ErrorCode.MERCENARY_003, HttpStatus.UNPROCESSABLE_ENTITY,
                "Chỉ có thể tuyển mercenary sau khi kèo đã ghép");
        assertMercenary(ErrorCode.MERCENARY_004, HttpStatus.UNPROCESSABLE_ENTITY, "Kèo đã kết thúc hoặc đã bị huỷ");
        assertMercenary(ErrorCode.MERCENARY_005, HttpStatus.UNPROCESSABLE_ENTITY, "Đội tuyển hiện không hoạt động");
        assertMercenary(ErrorCode.MERCENARY_006, HttpStatus.CONFLICT, "Tin tuyển đã đủ người");
        assertMercenary(ErrorCode.MERCENARY_007, HttpStatus.CONFLICT, "Tin tuyển đã đóng hoặc hết hạn");
        assertMercenary(ErrorCode.MERCENARY_008, HttpStatus.UNPROCESSABLE_ENTITY,
                "Số lượng không được thấp hơn số người đã duyệt");
        assertMercenary(ErrorCode.MERCENARY_009, HttpStatus.CONFLICT, "Bạn đã ứng tuyển tin này");
        assertMercenary(ErrorCode.MERCENARY_010, HttpStatus.UNPROCESSABLE_ENTITY,
                "Thành viên của đội trong trận không thể làm mercenary cho trận này");
        assertMercenary(ErrorCode.MERCENARY_011, HttpStatus.UNPROCESSABLE_ENTITY, "Đã có đơn được duyệt ở trận trùng giờ");
        assertMercenary(ErrorCode.MERCENARY_012, HttpStatus.CONFLICT, "Trạng thái đơn đã thay đổi, vui lòng tải lại");
        assertMercenary(ErrorCode.MERCENARY_013, HttpStatus.UNPROCESSABLE_ENTITY, "Trận đã bắt đầu");
        assertMercenary(ErrorCode.MERCENARY_014, HttpStatus.FORBIDDEN, "Bạn không tham gia đơn ứng tuyển này");
        assertMercenary(ErrorCode.MERCENARY_015, HttpStatus.UNPROCESSABLE_ENTITY,
                "Chỉ đánh giá được sau trận mà mercenary đã tham gia");
        assertMercenary(ErrorCode.MERCENARY_016, HttpStatus.UNPROCESSABLE_ENTITY, "Chỉ đội trưởng được chọn 'Không đến'");
        assertMercenary(ErrorCode.MERCENARY_017, HttpStatus.CONFLICT, "Bạn đã đánh giá người chơi này");
        assertMercenary(ErrorCode.MERCENARY_018, HttpStatus.UNPROCESSABLE_ENTITY,
                "Bật GPS hoặc cập nhật khu vực trong hồ sơ để tìm kèo");
        assertMercenary(ErrorCode.MERCENARY_019, HttpStatus.UNPROCESSABLE_ENTITY,
                "Bạn vừa là ứng viên vừa là đội trưởng của đơn này, không thể thực hiện");
        assertMercenary(ErrorCode.MERCENARY_020, HttpStatus.UNPROCESSABLE_ENTITY,
                "Chỉ report được sau trận đã tham gia, hoặc khi đã nhắn tin");
        assertMercenary(ErrorCode.MERCENARY_021, HttpStatus.UNPROCESSABLE_ENTITY, "Đơn này chỉ report được giao tiếp tệ");
        assertMercenary(ErrorCode.MERCENARY_022, HttpStatus.CONFLICT, "Bạn đã report loại này trong trận");
        assertMercenary(ErrorCode.MERCENARY_023, HttpStatus.CONFLICT,
                "Đội đã có tin đang mở cho vị trí này, hãy tăng số lượng");
        assertMercenary(ErrorCode.MERCENARY_024, HttpStatus.NOT_FOUND, "Không tìm thấy trận ngoài hệ thống");
        assertMercenary(ErrorCode.MERCENARY_025, HttpStatus.CONFLICT,
                "Trận đã có mercenary được duyệt, không sửa được sân, giờ hoặc trình độ");
    }

    private static void assertMercenary(ErrorCode code, HttpStatus status, String message) {
        assertThat(code.getCode()).isEqualTo(code.name());
        assertThat(code.getHttpStatus()).isEqualTo(status);
        assertThat(code.getMessage()).isEqualTo(message);
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
