package com.phananh.arenaflow.common.exception;

import org.springframework.http.HttpStatus;

/** Danh mục mã lỗi nghiệp vụ trả cho client: mỗi mã gắn HTTP status và thông báo tiếng Việt mặc định. */
public enum ErrorCode {
    VALIDATION_ERROR("VALIDATION_ERROR", HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ"),
    AUTH_001("AUTH_001", HttpStatus.UNAUTHORIZED, "Token không hợp lệ hoặc đã hết hạn"),
    AUTH_002("AUTH_002", HttpStatus.UNAUTHORIZED, "Refresh token không hợp lệ hoặc đã hết hạn"),
    AUTH_003("AUTH_003", HttpStatus.FORBIDDEN, "Tài khoản đã bị xoá"),
    AUTH_004("AUTH_004", HttpStatus.FORBIDDEN, "Cần quyền quản trị"),
    USER_001("USER_001", HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"),
    USER_002("USER_002", HttpStatus.BAD_REQUEST, "Khu vực không hợp lệ"),
    USER_003("USER_003", HttpStatus.UNPROCESSABLE_ENTITY, "Bạn phải chuyển quyền đội trưởng hoặc giải tán đội trước khi xóa tài khoản"),
    TEAM_001("TEAM_001", HttpStatus.NOT_FOUND, "Không tìm thấy đội"),
    TEAM_002("TEAM_002", HttpStatus.FORBIDDEN, "Chỉ đội trưởng được thực hiện"),
    TEAM_003("TEAM_003", HttpStatus.CONFLICT, "Người này đã là thành viên hoặc đã được mời"),
    TEAM_004("TEAM_004", HttpStatus.NOT_FOUND, "Không tìm thấy lời mời"),
    TEAM_005("TEAM_005", HttpStatus.FORBIDDEN, "Chỉ người được mời được xử lý lời mời"),
    TEAM_006("TEAM_006", HttpStatus.CONFLICT, "Lời mời đã được xử lý"),
    TEAM_007("TEAM_007", HttpStatus.UNPROCESSABLE_ENTITY, "Chỉ chuyển quyền cho thành viên đang hoạt động"),
    TEAM_008("TEAM_008", HttpStatus.UNPROCESSABLE_ENTITY, "Không thể chuyển quyền cho chính mình"),
    TEAM_009("TEAM_009", HttpStatus.UNPROCESSABLE_ENTITY, "Đội trưởng phải chuyển quyền trước khi rời đội"),
    TEAM_010("TEAM_010", HttpStatus.UNPROCESSABLE_ENTITY, "Bạn không phải thành viên của đội"),
    TEAM_011("TEAM_011", HttpStatus.CONFLICT, "Đội đã ở trạng thái INACTIVE"),
    TEAM_012("TEAM_012", HttpStatus.CONFLICT, "Đội đang hoạt động"),
    TEAM_013("TEAM_013", HttpStatus.UNPROCESSABLE_ENTITY, "Đội không còn đội trưởng, không thể kích hoạt lại"),
    TEAM_014("TEAM_014", HttpStatus.CONFLICT, "Dữ liệu vừa được cập nhật bởi thao tác khác, vui lòng thử lại"),
    TEAM_015("TEAM_015", HttpStatus.UNPROCESSABLE_ENTITY, "Đội đang tạm ngưng hoạt động"),
    TEAM_016("TEAM_016", HttpStatus.UNPROCESSABLE_ENTITY, "Đội đã giải tán"),
    VENUE_001("VENUE_001", HttpStatus.NOT_FOUND, "Không tìm thấy địa điểm"),
    VENUE_002("VENUE_002", HttpStatus.UNPROCESSABLE_ENTITY, "Địa điểm đã ngừng hoạt động"),
    VENUE_003("VENUE_003", HttpStatus.UNPROCESSABLE_ENTITY, "Tối đa 5 ảnh mỗi địa điểm"),
    VENUE_004("VENUE_004", HttpStatus.NOT_FOUND, "Không tìm thấy ảnh"),
    VENUE_005("VENUE_005", HttpStatus.BAD_REQUEST, "Ảnh tải lên không hợp lệ"),
    VENUE_006("VENUE_006", HttpStatus.CONFLICT, "Ảnh này đã được lưu"),
    VENUE_007("VENUE_007", HttpStatus.CONFLICT, "Địa điểm đã ở trạng thái ngừng hoạt động"),
    VENUE_008("VENUE_008", HttpStatus.CONFLICT, "Địa điểm đang hoạt động"),
    VENUE_009("VENUE_009", HttpStatus.NOT_FOUND, "Không tìm thấy báo cáo địa điểm"),
    VENUE_010("VENUE_010", HttpStatus.CONFLICT, "Báo cáo đã được xử lý"),
    VENUE_011("VENUE_011", HttpStatus.CONFLICT, "Dữ liệu vừa được cập nhật bởi thao tác khác, vui lòng thử lại"),
    VENUE_012("VENUE_012", HttpStatus.SERVICE_UNAVAILABLE, "Dịch vụ lưu ảnh tạm thời không khả dụng"),
    VENUE_013("VENUE_013", HttpStatus.FORBIDDEN, "Chỉ người tạo địa điểm hoặc quản trị viên được thực hiện"),
    VENUE_014("VENUE_014", HttpStatus.CONFLICT, "Bạn đã report địa điểm này, đang chờ admin xử lý"),
    VENUE_015("VENUE_015", HttpStatus.FORBIDDEN, "Quản trị viên không report địa điểm; hãy sửa hoặc đánh dấu ngừng hoạt động trực tiếp"),
    VENUE_016("VENUE_016", HttpStatus.UNPROCESSABLE_ENTITY, "Địa điểm cần bổ sung phường/xã"),
    VENUE_017("VENUE_017", HttpStatus.FORBIDDEN, "Chỉ quản trị viên được nhập số điện thoại và website của địa điểm"),
    VENUE_018("VENUE_018", HttpStatus.BAD_REQUEST,
            "Danh sách này chỉ gồm địa điểm đang hoạt động; quản trị viên dùng danh sách quản trị"),
    MATCH_001("MATCH_001", HttpStatus.NOT_FOUND, "Không tìm thấy kèo"),
    MATCH_002("MATCH_002", HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu ghép kèo"),
    MATCH_003("MATCH_003", HttpStatus.CONFLICT, "Kèo đã được ghép"),
    MATCH_004("MATCH_004", HttpStatus.CONFLICT, "Kèo đã hết hạn"),
    MATCH_005("MATCH_005", HttpStatus.CONFLICT, "Kèo không còn ở trạng thái cho phép thao tác này"),
    MATCH_006("MATCH_006", HttpStatus.CONFLICT, "Đội đã gửi yêu cầu cho kèo này"),
    MATCH_007("MATCH_007", HttpStatus.UNPROCESSABLE_ENTITY, "Không thể gửi yêu cầu cho kèo của đội mình"),
    MATCH_008("MATCH_008", HttpStatus.CONFLICT, "Yêu cầu đã được xử lý"),
    MATCH_009("MATCH_009", HttpStatus.FORBIDDEN, "Đội không có quyền với kèo này"),
    MATCH_010("MATCH_010", HttpStatus.UNPROCESSABLE_ENTITY, "Vui lòng từ chối tất cả yêu cầu trước khi hủy"),
    MATCH_011("MATCH_011", HttpStatus.UNPROCESSABLE_ENTITY, "Vui lòng nhập lý do hủy"),
    MATCH_012("MATCH_012", HttpStatus.UNPROCESSABLE_ENTITY, "Trận đã bắt đầu, không thể hủy"),
    MATCH_013("MATCH_013", HttpStatus.UNPROCESSABLE_ENTITY, "Đội đối phương hiện không hoạt động"),
    MATCH_014("MATCH_014", HttpStatus.CONFLICT, "Dữ liệu vừa được cập nhật bởi thao tác khác, vui lòng thử lại"),
    MATCH_015("MATCH_015", HttpStatus.CONFLICT, "Trạng thái đã thay đổi, vui lòng tải lại"),
    MATCH_016("MATCH_016", HttpStatus.UNPROCESSABLE_ENTITY, "Thao tác không áp dụng cho loại kèo này"),
    SPORT_001("SPORT_001", HttpStatus.NOT_FOUND, "Không tìm thấy bộ môn"),
    SPORT_002("SPORT_002", HttpStatus.UNPROCESSABLE_ENTITY, "Bộ môn hiện không được hỗ trợ"),
    CHAT_001("CHAT_001", HttpStatus.UNPROCESSABLE_ENTITY, "Tin nhắn mốc không thuộc cuộc trò chuyện này"),
    CHAT_002("CHAT_002", HttpStatus.NOT_FOUND, "Không tìm thấy cuộc trò chuyện"),
    CHAT_003("CHAT_003", HttpStatus.FORBIDDEN, "Bạn không tham gia cuộc trò chuyện này"),
    CHAT_004("CHAT_004", HttpStatus.CONFLICT, "Cuộc trò chuyện chỉ còn xem"),
    PAIRING_001("PAIRING_001", HttpStatus.NOT_FOUND, "Không tìm thấy thương lượng ghép kèo"),
    PAIRING_002("PAIRING_002", HttpStatus.FORBIDDEN, "Đội không tham gia thương lượng này"),
    PAIRING_003("PAIRING_003", HttpStatus.CONFLICT, "Đội đã nhận kèo này"),
    PAIRING_004("PAIRING_004", HttpStatus.UNPROCESSABLE_ENTITY, "Không thể nhận kèo của đội mình"),
    PAIRING_005("PAIRING_005", HttpStatus.CONFLICT, "Thương lượng đã kết thúc"),
    PAIRING_006("PAIRING_006", HttpStatus.CONFLICT, "Phương án đã thay đổi, vui lòng tải lại"),
    PAIRING_007("PAIRING_007", HttpStatus.UNPROCESSABLE_ENTITY, "Chưa chốt địa điểm và giờ"),
    PAIRING_008("PAIRING_008", HttpStatus.UNPROCESSABLE_ENTITY, "Địa điểm hoặc giờ không thuộc lựa chọn của kèo"),
    // Đánh giá, report, tranh chấp.
    RATING_001("RATING_001", HttpStatus.UNPROCESSABLE_ENTITY, "Chỉ đánh giá được trận đã kết thúc"),
    RATING_002("RATING_002", HttpStatus.CONFLICT, "Bạn đã đánh giá trận này"),
    RATING_003("RATING_003", HttpStatus.UNPROCESSABLE_ENTITY, "Bạn là đội trưởng của cả hai đội, không thể đánh giá trận này"),
    REPORT_001("REPORT_001", HttpStatus.NOT_FOUND, "Không tìm thấy report"),
    REPORT_002("REPORT_002", HttpStatus.UNPROCESSABLE_ENTITY, "Chỉ report được trận đã kết thúc"),
    REPORT_003("REPORT_003", HttpStatus.UNPROCESSABLE_ENTITY, "Kèo đã huỷ chỉ report được giao tiếp tệ"),
    REPORT_004("REPORT_004", HttpStatus.CONFLICT, "Bạn đã report đội này với loại này trong trận"),
    REPORT_005("REPORT_005", HttpStatus.UNPROCESSABLE_ENTITY, "Bạn là đội trưởng của cả hai đội, không thể report trận này"),
    REPORT_006("REPORT_006", HttpStatus.UNPROCESSABLE_ENTITY, "Chỉ tranh chấp được report bùng"),
    REPORT_007("REPORT_007", HttpStatus.UNPROCESSABLE_ENTITY, "Đã quá hạn 24 giờ để tranh chấp"),
    REPORT_008("REPORT_008", HttpStatus.CONFLICT, "Report đã được tranh chấp hoặc đã có quyết định"),
    REPORT_009("REPORT_009", HttpStatus.FORBIDDEN, "Chỉ người bị report được tranh chấp"),
    REPORT_010("REPORT_010", HttpStatus.UNPROCESSABLE_ENTITY, "Chỉ report qua chat được khi hai đội đã nhắn tin"),
    REPORT_011("REPORT_011", HttpStatus.UNPROCESSABLE_ENTITY, "Cuộc trò chuyện này không report theo cách này"),
    DISPUTE_001("DISPUTE_001", HttpStatus.NOT_FOUND, "Không tìm thấy tranh chấp"),
    DISPUTE_002("DISPUTE_002", HttpStatus.CONFLICT, "Tranh chấp đã được xử lý"),
    // Lỗi tầng HTTP/hệ thống (không gắn miền nghiệp vụ). SYS_004/SYS_005: response giữ status gốc của exception.
    SYS_001("SYS_001", HttpStatus.NOT_FOUND, "Không tìm thấy tài nguyên"),
    SYS_002("SYS_002", HttpStatus.METHOD_NOT_ALLOWED, "Phương thức không được hỗ trợ"),
    SYS_003("SYS_003", HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Kiểu nội dung không được hỗ trợ"),
    SYS_004("SYS_004", HttpStatus.BAD_REQUEST, "Yêu cầu không hợp lệ"),
    SYS_005("SYS_005", HttpStatus.INTERNAL_SERVER_ERROR, "Đã xảy ra lỗi hệ thống");

    private final String code;
    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(String code, HttpStatus httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getMessage() {
        return message;
    }
}
