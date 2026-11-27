package com.phananh.arenaflow.application.support;

import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;

/**
 * Kết quả một lần thử ghi kèo. Lần thử gặp kèo quá hạn trả {@link Expired} để transaction COMMIT việc chuyển EXPIRED;
 * service gọi {@link #valueOrThrowExpired()} ngoài vòng thử lại mới ném MATCH_004 (ném trong lần thử sẽ rollback).
 */
public sealed interface MatchWriteOutcome<T> {

    record Done<T>(T value) implements MatchWriteOutcome<T> {
    }

    record Expired<T>() implements MatchWriteOutcome<T> {
    }

    static <T> MatchWriteOutcome<T> done(T value) {
        return new Done<>(value);
    }

    static <T> MatchWriteOutcome<T> expired() {
        return new Expired<>();
    }

    default T valueOrThrowExpired() {
        return switch (this) {
            case Done<T> done -> done.value();
            case Expired<T> ignored -> throw new BusinessException(ErrorCode.MATCH_004);
        };
    }
}
