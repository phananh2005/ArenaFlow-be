package com.phananh.arenaflow.application.support;

import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatchWriteOutcomeTest {

    @Test
    void doneReturnsValue() {
        assertThat(MatchWriteOutcome.done("ok").valueOrThrowExpired()).isEqualTo("ok");
    }

    @Test
    void expiredThrowsMatch004() {
        MatchWriteOutcome<String> outcome = MatchWriteOutcome.expired();

        assertThatThrownBy(outcome::valueOrThrowExpired).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MATCH_004));
    }
}
