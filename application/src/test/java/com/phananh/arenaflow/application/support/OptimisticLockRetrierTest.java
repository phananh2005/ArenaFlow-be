package com.phananh.arenaflow.application.support;

import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@ExtendWith(OutputCaptureExtension.class)
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

    @Test
    void retriesDeadlockUpToThreeAttempts() {
        OptimisticLockRetrier retrier = new OptimisticLockRetrier(transactionTemplate());
        AtomicInteger attempts = new AtomicInteger();

        String result = retrier.execute(() -> {
            if (attempts.incrementAndGet() < 3) {
                throw deadlock();
            }
            return "success";
        });

        assertThat(result).isEqualTo("success");
        assertThat(attempts).hasValue(3);
    }

    @Test
    void throwsTeam014AfterThreeDeadlocks() {
        OptimisticLockRetrier retrier = new OptimisticLockRetrier(transactionTemplate());
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> retrier.execute(() -> {
            attempts.incrementAndGet();
            throw deadlock();
        }))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TEAM_014));
        assertThat(attempts).hasValue(3);
    }

    @Test
    void doesNotRetryLockFailuresOtherThanDeadlockSuchAsNowait() {
        OptimisticLockRetrier retrier = new OptimisticLockRetrier(transactionTemplate());
        AtomicInteger attempts = new AtomicInteger();
        PessimisticLockingFailureException lockNotAvailable = new PessimisticLockingFailureException(
                "could not obtain lock", new SQLException("could not obtain lock on row", "55P03"));

        assertThatThrownBy(() -> retrier.execute(() -> {
            attempts.incrementAndGet();
            throw lockNotAvailable;
        }))
                .isSameAs(lockNotAvailable);
        assertThat(attempts).hasValue(1);
    }

    @Test
    void logsWarnWithSqlStateAndAttemptWhenRetryingDeadlock(CapturedOutput output) {
        OptimisticLockRetrier retrier = new OptimisticLockRetrier(transactionTemplate());
        AtomicInteger attempts = new AtomicInteger();

        retrier.execute(() -> {
            if (attempts.incrementAndGet() < 2) {
                throw deadlock();
            }
            return "success";
        });

        assertThat(warnLine(output, "attempt 1/3")).contains("Deadlock (SQLState 40P01) on attempt 1/3, retrying");
        assertThat(output.getAll()).as("log không lộ message/câu SQL (kể cả stack trace)")
                .doesNotContain("deadlock detected");
    }

    @Test
    void logsGivingUpOnLastDeadlockAttempt(CapturedOutput output) {
        OptimisticLockRetrier retrier = new OptimisticLockRetrier(transactionTemplate());

        assertThatThrownBy(() -> retrier.execute(() -> {
            throw deadlock();
        }))
                .isInstanceOf(BusinessException.class);

        assertThat(warnLine(output, "attempt 3/3")).contains("Deadlock (SQLState 40P01) on attempt 3/3, giving up");
        assertThat(output.getAll()).as("log không lộ message/câu SQL (kể cả stack trace)")
                .doesNotContain("deadlock detected");
    }

    /** Dòng log WARN duy nhất chứa {@code marker} — các assert sau đó kiểm trên cùng một dòng. */
    private static String warnLine(CapturedOutput output, String marker) {
        List<String> lines = output.getAll().lines()
                .filter(line -> line.contains("WARN") && line.contains(marker))
                .toList();
        assertThat(lines).as("WARN lines containing '%s'", marker).hasSize(1);
        return lines.get(0);
    }

    @Test
    void doesNotRetrySerializationFailure() {
        OptimisticLockRetrier retrier = new OptimisticLockRetrier(transactionTemplate());
        AtomicInteger attempts = new AtomicInteger();
        PessimisticLockingFailureException serializationFailure = new PessimisticLockingFailureException(
                "could not serialize access", new SQLException("could not serialize access", "40001"));

        assertThatThrownBy(() -> retrier.execute(() -> {
            attempts.incrementAndGet();
            throw serializationFailure;
        }))
                .isSameAs(serializationFailure);
        assertThat(attempts).hasValue(1);
    }

    @Test
    void doesNotRetryOtherFailures() {
        OptimisticLockRetrier retrier = new OptimisticLockRetrier(transactionTemplate());
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> retrier.execute(() -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("boom");
        }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(attempts).hasValue(1);
    }

    private static CannotAcquireLockException deadlock() {
        return new CannotAcquireLockException("deadlock detected", new SQLException("deadlock detected", "40P01"));
    }

    private TransactionTemplate transactionTemplate() {
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus transactionStatus = mock(TransactionStatus.class);
        org.mockito.Mockito.when(transactionManager.getTransaction(org.mockito.ArgumentMatchers.any()))
                .thenReturn(transactionStatus);
        return new TransactionTemplate(transactionManager);
    }
}
