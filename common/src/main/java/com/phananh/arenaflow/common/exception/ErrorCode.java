package com.phananh.arenaflow.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_ERROR("VALIDATION_ERROR", HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ"),
    AUTH_001("AUTH_001", HttpStatus.UNAUTHORIZED, "Token không hợp lệ hoặc đã hết hạn"),
    AUTH_002("AUTH_002", HttpStatus.UNAUTHORIZED, "Refresh token không hợp lệ hoặc đã hết hạn"),
    AUTH_003("AUTH_003", HttpStatus.FORBIDDEN, "Tài khoản đã bị xoá"),
    AUTH_004("AUTH_004", HttpStatus.FORBIDDEN, "Cần quyền quản trị"),
    USER_001("USER_001", HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"),
    USER_002("USER_002", HttpStatus.BAD_REQUEST, "Khu vực không hợp lệ"),
    USER_003("USER_003", HttpStatus.UNPROCESSABLE_ENTITY, "Bạn phải chuyển quyền đội trưởng trước khi xoá tài khoản"),
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
