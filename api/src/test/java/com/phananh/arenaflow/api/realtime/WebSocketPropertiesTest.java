package com.phananh.arenaflow.api.realtime;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebSocketPropertiesTest {

    @Test
    void defaultsAreSameOriginOnlyAndTenSecondHeartbeat() {
        WebSocketProperties properties = new WebSocketProperties(null, null);

        assertThat(properties.allowedOriginPatterns()).isEmpty();
        assertThat(properties.heartbeatMs()).isEqualTo(10_000L);
    }

    @Test
    void blankPatternsAreDroppedAndWildcardAnyOriginIsRejected() {
        assertThat(new WebSocketProperties(List.of(" ", "https://fe.example"), 5_000L).allowedOriginPatterns())
                .containsExactly("https://fe.example");
        assertThatThrownBy(() -> new WebSocketProperties(List.of("*"), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
