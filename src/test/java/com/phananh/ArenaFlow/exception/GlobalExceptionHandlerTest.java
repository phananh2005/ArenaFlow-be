package com.phananh.ArenaFlow.exception;

import jakarta.validation.constraints.Min;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

    private static final String PHONE = "0901234567";
    private static final String PHONE_UNIQUE_INDEX = "idx_user_phone_unique";

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

        // Lỗi nghiệp vụ 4xx → WARN kèm mã lỗi + path.
        assertThat(output.getOut()).contains("WARN").contains("USER_001").contains("/test/business-error");
    }

    @Test
    void unexpectedExceptionReturns500WithoutStackTraceButLogsError(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("SYS_005"))
                .andExpect(jsonPath("$.error.details").doesNotExist());

        // 5xx → ERROR kèm exception để điều tra; response không lộ chi tiết.
        assertThat(output.getOut()).contains("ERROR").contains("IllegalStateException").contains("boom");
    }

    @Test
    void dataIntegrityViolationWithNullCauseMessageFallsBackTo500InsteadOfNpe() throws Exception {
        mockMvc.perform(get("/test/integrity-null-message"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("SYS_005"));
    }

    @Test
    void dataIntegrityViolationLogsConstraintNameButNotRowValues(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/test/integrity-phone"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("SYS_005"));

        // security-backend: không log PII — giá trị dòng trong "Detail: Key (...)=(...)" không được vào log.
        assertThat(output.getOut()).contains("ERROR").contains(PHONE_UNIQUE_INDEX).doesNotContain(PHONE);
    }

    @Test
    void dataIntegrityViolationWithoutConstraintNameLogsUnknown(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/test/integrity-no-constraint"))
                .andExpect(status().isInternalServerError());

        assertThat(output.getOut()).contains("constraint=unknown").doesNotContain("constraint=null");
    }

    @Test
    void responseStatusExceptionKeepsStatusWithoutLeakingReason() throws Exception {
        mockMvc.perform(get("/test/status-409"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SYS_004"))
                .andExpect(jsonPath("$.error.message").value("Yêu cầu không hợp lệ"))
                .andExpect(jsonPath("$.error.details").doesNotExist())
                .andExpect(content().string(not(containsString("internal reason"))));
    }

    @Test
    void framework5xxKeepsOriginalStatusWithSys005() throws Exception {
        mockMvc.perform(get("/test/status-503"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("SYS_005"));
    }

    @Test
    void nonStandardFrameworkStatusFallsBackTo500Envelope() throws Exception {
        mockMvc.perform(get("/test/status-499"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("SYS_005"));
    }

    @Test
    void missingRequiredQueryParamIsValidationErrorWithField() throws Exception {
        mockMvc.perform(get("/test/required-param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.validationErrors[0].field").value("q"));
    }

    @Test
    void methodParameterValidationReportsParameterNameAsField() throws Exception {
        mockMvc.perform(get("/test/min-param").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.validationErrors[0].field").value("page"));
    }

    @Test
    void otherFramework400IsValidationError() throws Exception {
        mockMvc.perform(get("/test/required-header"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @RestController
    static class FailingController {
        @GetMapping("/test/status-409")
        void throwConflictStatus() {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "internal reason");
        }

        @GetMapping("/test/status-503")
        void throwServiceUnavailable() {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        }

        @GetMapping("/test/integrity-no-constraint")
        void throwIntegrityWithoutConstraintName() {
            throw new DataIntegrityViolationException("could not execute statement",
                    new org.hibernate.exception.ConstraintViolationException(
                            "could not execute statement", new SQLException("ERROR: violation", "23505"), null));
        }

        @GetMapping("/test/status-499")
        void throwNonStandardStatus() {
            throw new ResponseStatusException(HttpStatusCode.valueOf(499));
        }

        /** Không có @Validated ở class → Spring MVC tự validate tham số → HandlerMethodValidationException. */
        @GetMapping("/test/min-param")
        void minParam(@RequestParam @Min(1) int page) {
        }

        @GetMapping("/test/required-param")
        void requireParam(@RequestParam String q) {
        }

        @GetMapping("/test/required-header")
        void requireHeader(@RequestHeader("X-Probe") String probe) {
        }

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

        /** Dạng lỗi PostgreSQL thật khi đụng unique: message chứa giá trị dòng ở phần Detail. */
        @GetMapping("/test/integrity-phone")
        void throwIntegrityWithRowValues() {
            String sqlMessage = "ERROR: duplicate key value violates unique constraint \"" + PHONE_UNIQUE_INDEX + "\"\n"
                    + "  Detail: Key (phone_number)=(" + PHONE + ") already exists.";
            SQLException sql = new SQLException(sqlMessage, "23505");
            throw new DataIntegrityViolationException("could not execute statement [" + sqlMessage + "]",
                    new org.hibernate.exception.ConstraintViolationException(
                            "could not execute statement", sql, PHONE_UNIQUE_INDEX));
        }
    }
}
