package com.phananh.arenaflow.application.support;

import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.util.function.Supplier;

@Slf4j
@Component
public class OptimisticLockRetrier {

    private static final int MAX_ATTEMPTS = 3;
    private static final String DEADLOCK_DETECTED = "40P01";

    private final TransactionTemplate transactionTemplate;

    public OptimisticLockRetrier(TransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Mỗi lần thử bọc trong {@link TransactionTemplate} (propagation REQUIRED): tối đa 3 lần khi optimistic lock xung
     * đột hoặc deadlock (PostgreSQL huỷ một bên), hết lượt → TEAM_014. Mỗi lần gặp deadlock ghi WARN kèm SQLState để
     * theo dõi (chỉ mã + số lần thử, không ghi câu SQL). Lỗi khoá khác (NOWAIT, lock timeout, serialization failure)
     * ném nguyên, không thử lại. Caller không được mở transaction sẵn, nếu không các lần thử dùng chung transaction đã
     * hỏng.
     */
    public <T> T execute(Supplier<T> operation) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return transactionTemplate.execute(status -> operation.get());
            } catch (ObjectOptimisticLockingFailureException exception) {
                // thử lại; hết lượt → TEAM_014 sau vòng lặp
            } catch (PessimisticLockingFailureException exception) {
                if (!isDeadlock(exception)) {
                    throw exception;
                }
                log.warn("Deadlock (SQLState {}) on attempt {}/{}{}", DEADLOCK_DETECTED, attempt, MAX_ATTEMPTS,
                        attempt == MAX_ATTEMPTS ? ", giving up" : ", retrying");
            }
        }
        throw new BusinessException(ErrorCode.TEAM_014);
    }

    private static boolean isDeadlock(PessimisticLockingFailureException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && DEADLOCK_DETECTED.equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }
}
