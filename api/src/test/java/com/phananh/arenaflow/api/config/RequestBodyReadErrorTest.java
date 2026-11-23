package com.phananh.arenaflow.api.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lỗi đọc JSON body: ngày giờ kèm múi giờ, số thập phân / chuỗi số vào field số nguyên bị từ chối; field là đường
 * dẫn. Chuỗi rỗng vào field số nguyên kiểu object (Integer, Long) đọc như null; vào int thì bị từ chối.
 */
class RequestBodyReadErrorTest {

    private static final String LOCAL_DATE_TIME = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,6})?$";

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BodyController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(JsonTestSupport.applicationJsonMapper()))
                .build();
    }

    @Test
    void localDateTimeWithoutOffsetIsAcceptedAsIs() throws Exception {
        postBody("{\"startsAt\":\"2026-11-20T19:00:00\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startsAt").value("2026-11-20T19:00:00"));
        postBody("{\"startsAt\":\"2026-11-20T19:00\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startsAt").value("2026-11-20T19:00:00"));
    }

    @Test
    void localDateTimeWithZuluIsRejectedWithFieldPath() throws Exception {
        expectFieldError("{\"startsAt\":\"2026-11-20T12:00:00Z\"}", "startsAt");
    }

    @Test
    void localDateTimeWithOffsetIsRejectedWithFieldPath() throws Exception {
        expectFieldError("{\"startsAt\":\"2026-11-20T19:00:00+07:00\"}", "startsAt");
    }

    @Test
    void nestedArrayElementReportsIndexedPath() throws Exception {
        expectFieldError("{\"timeOptions\":[{\"startsAt\":\"2026-11-20T12:00:00Z\"}]}", "timeOptions[0].startsAt");
    }

    @Test
    void wrongDateFormatReportsFieldName() throws Exception {
        expectFieldError("{\"dateOfBirth\":\"15/05/1995\"}", "dateOfBirth");
    }

    @Test
    void wrongNumberEnumOrUuidReportsFieldName() throws Exception {
        expectFieldError("{\"count\":\"abc\"}", "count");
        expectFieldError("{\"level\":\"BOGUS\"}", "level");
        expectFieldError("{\"id\":\"not-a-uuid\"}", "id");
    }

    @Test
    void integerFieldsAcceptWholeNumbers() throws Exception {
        postBody("{\"score\":4,\"amount\":600000,\"items\":[{\"amount\":1}]}")
                .andExpect(status().isOk());
    }

    @Test
    void decimalIntoIntegerFieldIsRejectedWithFieldPath() throws Exception {
        expectFieldError("{\"score\":4.5}", "score");
        expectFieldError("{\"count\":4.5}", "count");
        expectFieldError("{\"amount\":600000.5}", "amount");
        expectFieldError("{\"items\":[{\"amount\":1.5}]}", "items[0].amount");
    }

    @Test
    void wholeNumberWrittenAsDecimalIsRejected() throws Exception {
        expectFieldError("{\"score\":4.0}", "score");
        expectFieldError("{\"amount\":600000.0}", "amount");
    }

    @Test
    void numericStringIntoIntegerFieldIsRejected() throws Exception {
        expectFieldError("{\"score\":\"4\"}", "score");
        expectFieldError("{\"count\":\"4\"}", "count");
        expectFieldError("{\"amount\":\"600000\"}", "amount");
    }

    @Test
    void primitiveIntFieldRejectsDecimalAndNumericString() throws Exception {
        postPrimitive("{\"rank\":4}").andExpect(status().isOk()).andExpect(jsonPath("$.rank").value(4));
        expectFieldError(postPrimitive("{\"rank\":4.5}"), "rank");
        expectFieldError(postPrimitive("{\"rank\":4.0}"), "rank");
        expectFieldError(postPrimitive("{\"rank\":\"4\"}"), "rank");
    }

    @Test
    void emptyStringIntoIntegerObjectFieldIsReadAsNull() throws Exception {
        postBody("{\"count\":\"\",\"amount\":\"\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").doesNotExist())
                .andExpect(jsonPath("$.amount").doesNotExist());
    }

    @Test
    void emptyStringIntoPrimitiveIntFieldIsRejected() throws Exception {
        expectFieldError(postPrimitive("{\"rank\":\"\"}"), "rank");
    }

    @Test
    void malformedOrMissingBodyFallsBackToBody() throws Exception {
        expectFieldError("{\"startsAt\":", "body");
        expectFieldError("", "body");
    }

    private void expectFieldError(String body, String field) throws Exception {
        expectFieldError(postBody(body), field);
    }

    private void expectFieldError(ResultActions result, String field) throws Exception {
        result
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.validationErrors[0].field").value(field))
                .andExpect(jsonPath("$.error.validationErrors[0].message").value("Nội dung request không hợp lệ"))
                .andExpect(jsonPath("$.error.timestamp").value(matchesPattern(LOCAL_DATE_TIME)));
    }

    private ResultActions postBody(String body) throws Exception {
        return mockMvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions postPrimitive(String body) throws Exception {
        return mockMvc.perform(post("/test/primitive").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    enum Level { LOW, HIGH }

    record TimeOption(LocalDateTime startsAt) {
    }

    record CostItem(Long amount) {
    }

    record ProbeRequest(LocalDateTime startsAt, List<TimeOption> timeOptions, LocalDate dateOfBirth, Integer count,
                        Level level, UUID id, Integer score, Long amount, List<CostItem> items) {
    }

    // Tách riêng: Jackson 3 từ chối khi field int nguyên thuỷ thiếu giá trị, gộp vào ProbeRequest làm mọi request khác lỗi.
    record PrimitiveRequest(int rank) {
    }

    @RestController
    static class BodyController {
        @PostMapping("/test/body")
        Map<String, Object> echo(@RequestBody ProbeRequest request) {
            Map<String, Object> echo = new HashMap<>();
            echo.put("startsAt", request.startsAt());
            echo.put("count", request.count());
            echo.put("amount", request.amount());
            return echo;
        }

        @PostMapping("/test/primitive")
        Map<String, Object> echoPrimitive(@RequestBody PrimitiveRequest request) {
            return Map.of("rank", request.rank());
        }
    }
}
