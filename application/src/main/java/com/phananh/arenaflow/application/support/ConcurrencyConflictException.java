package com.phananh.arenaflow.application.support;

/**
 * Điều kiện CAS ({@code UPDATE … WHERE <trạng thái kỳ vọng>}) không khớp: dữ liệu vừa bị thao tác khác đổi. Ném trong
 * lần thử của {@link ConcurrencyConflictRetrier} để rollback và thử lại; lần sau guard đọc dữ liệu mới và trả mã đúng.
 */
public class ConcurrencyConflictException extends RuntimeException {

    public ConcurrencyConflictException() {
        super("Concurrent update detected");
    }
}
