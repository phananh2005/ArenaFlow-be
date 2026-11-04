package com.phananh.arenaflow.application.support;

import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

@Component
public class OptimisticLockRetrier {

    private static final int MAX_ATTEMPTS = 3;

    private final TransactionTemplate transactionTemplate;

    public OptimisticLockRetrier(TransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    public <T> T execute(Supplier<T> operation) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return transactionTemplate.execute(status -> operation.get());
            } catch (ObjectOptimisticLockingFailureException exception) {
                if (attempt == MAX_ATTEMPTS) {
                    throw new BusinessException(ErrorCode.TEAM_014);
                }
            }
        }
        throw new BusinessException(ErrorCode.TEAM_014);
    }
}
