package com.phananh.arenaflow.application.support;

/**
 * Điều kiện của một câu ghi CAS tự viết ({@code UPDATE … WHERE status/revision/version}) không khớp: dữ liệu vừa bị
 * thao tác khác đổi. {@link ConcurrencyConflictRetrier} rollback lần thử và thử lại; lần sau đọc lại trạng thái mới
 * để guard trả mã nghiệp vụ đúng. Không mang dữ liệu người dùng.
 */
public class ConcurrencyConflictException extends RuntimeException {

    public ConcurrencyConflictException(String message) {
        super(message);
    }
}
