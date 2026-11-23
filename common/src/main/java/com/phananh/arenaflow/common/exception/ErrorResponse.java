package com.phananh.arenaflow.common.exception;

import com.phananh.arenaflow.common.time.AppTime;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Thân lỗi chung trả về client (bọc trong {@code error}).
 *
 * @param code             mã lỗi ({@link ErrorCode#getCode()})
 * @param message          thông điệp mặc định của mã lỗi
 * @param details          chi tiết thêm; null khi không có
 * @param timestamp        thời điểm phát sinh lỗi, giờ Việt Nam không kèm múi giờ
 * @param path             URI của request lỗi
 * @param validationErrors lỗi theo field; list rỗng khi không phải lỗi validation
 */
public record ErrorResponse(
        String code,
        String message,
        String details,
        LocalDateTime timestamp,
        String path,
        List<ValidationError> validationErrors) {

    /**
     * Lỗi không kèm danh sách field: {@code validationErrors} là list rỗng (không null), {@code timestamp} = thời
     * điểm gọi theo giờ Việt Nam.
     */
    public static ErrorResponse from(ErrorCode errorCode, String details, String path) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), details,
                AppTime.now(), path, List.of());
    }
}
