package com.phananh.arenaflow.application.dto.response;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    void wrapsPayloadInData() {
        ApiResponse<String> response = ApiResponse.of("value");

        assertThat(response.data()).isEqualTo("value");
    }
}
