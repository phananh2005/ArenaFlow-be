package com.phananh.arenaflow.api.config;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/** Bean {@code Clock}: theo zone của JVM và có độ phân giải micro giây. */
class ClockConfigTest {

    private final Clock clock = new ClockConfig().clock();

    @Test
    void clockUsesJvmZone() {
        assertThat(clock.getZone()).isEqualTo(ZoneId.systemDefault());
    }

    @Test
    void clockTicksInMicroseconds() {
        for (int i = 0; i < 1_000; i++) {
            assertThat(clock.instant().getNano() % 1_000).isZero();
        }
    }
}
