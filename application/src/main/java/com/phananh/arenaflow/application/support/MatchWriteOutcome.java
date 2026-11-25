package com.phananh.arenaflow.application.support;

import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;

/**
 * Kết quả một lần thử ghi trên kèo: {@link Expired} để transaction commit việc chuyển EXPIRED rồi service mới báo
 * MATCH_004 (ném exception trong lần thử sẽ rollback việc chuyển trạng thái).
 */
public sealed interface MatchWriteOutcome<T> {

    record Done<T>(T value) implements MatchWriteOutcome<T> {
    }

    record Expired<T>() implements MatchWriteOutcome<T> {
    }

    default T valueOrThrowExpired() {
        if (this instanceof Done<T> done) {
            return done.value();
        }
        throw new BusinessException(ErrorCode.MATCH_004);
    }
}
