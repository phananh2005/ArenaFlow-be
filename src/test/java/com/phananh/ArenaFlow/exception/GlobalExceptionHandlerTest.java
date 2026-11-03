package com.phananh.ArenaFlow.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void mapsBusinessExceptionToConfiguredStatusAndErrorEnvelope(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/test/business-error"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_001"))
                .andExpect(jsonPath("$.error.message").value("Không tìm thấy người dùng"))
                .andExpect(jsonPath("$.error.details").value("missing"))
                .andExpect(jsonPath("$.error.path").value("/test/business-error"));

        // Processing_Flow §6: lỗi nghiệp vụ 4xx → WARN kèm mã lỗi + path.
        assertThat(output.getOut()).contains("WARN").contains("USER_001").contains("/test/business-error");
    }

    @Test
    void unexpectedExceptionReturns500WithoutStackTraceButLogsError(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.details").doesNotExist());

        // 5xx → ERROR kèm exception để điều tra; response không lộ chi tiết.
        assertThat(output.getOut()).contains("ERROR").contains("IllegalStateException").contains("boom");
    }

    @Test
    void dataIntegrityViolationWithNullCauseMessageFallsBackTo500InsteadOfNpe() throws Exception {
        mockMvc.perform(get("/test/integrity-null-message"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"));
    }

    @RestController
    static class FailingController {
        @GetMapping("/test/business-error")
        void throwBusinessException() {
            throw new BusinessException(ErrorCode.USER_001, "missing");
        }

        @GetMapping("/test/unexpected")
        void throwUnexpected() {
            throw new IllegalStateException("boom");
        }

        @GetMapping("/test/integrity-null-message")
        void throwIntegrityWithNullMessage() {
            throw new DataIntegrityViolationException("wrapper", new RuntimeException((String) null));
        }
    }
}
