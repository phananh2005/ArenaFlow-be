package com.phananh.arenaflow.application.support;

import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class OptimisticLockRetrierTest {

    @Test
    void retriesOptimisticLockFailureUpToThreeAttempts() {
        OptimisticLockRetrier retrier = new OptimisticLockRetrier(transactionTemplate());
        AtomicInteger attempts = new AtomicInteger();

        String result = retrier.execute(() -> {
            if (attempts.incrementAndGet() < 3) {
                throw new ObjectOptimisticLockingFailureException("Team", "team-id");
            }
            return "success";
        });

        assertThat(result).isEqualTo("success");
        assertThat(attempts).hasValue(3);
    }

    @Test
    void throwsTeam014AfterThreeOptimisticLockFailures() {
        OptimisticLockRetrier retrier = new OptimisticLockRetrier(transactionTemplate());
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> retrier.execute(() -> {
            attempts.incrementAndGet();
            throw new ObjectOptimisticLockingFailureException("Team", "team-id");
        }))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TEAM_014));
        assertThat(attempts).hasValue(3);
    }

    private TransactionTemplate transactionTemplate() {
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus transactionStatus = mock(TransactionStatus.class);
        org.mockito.Mockito.when(transactionManager.getTransaction(org.mockito.ArgumentMatchers.any()))
                .thenReturn(transactionStatus);
        return new TransactionTemplate(transactionManager);
    }
}
