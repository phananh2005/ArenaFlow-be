package com.phananh.arenaflow.api.config;

import com.phananh.arenaflow.api.ApiIntegrationTest;

import com.phananh.arenaflow.domain.entity.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Lỗi 4xx do framework phát hiện (URL/method/Content-Type sai) phải giữ status gốc, không thành 500. */
@ExtendWith(OutputCaptureExtension.class)
class FrameworkErrorStatusTest extends ApiIntegrationTest {

    @Test
    void unknownUrlReturns404(CapturedOutput output) throws Exception {
        User user = saveUser("Unknown URL");
        mockMvc.perform(as(user, get("/api/v1/does-not-exist")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SYS_001"))
                .andExpect(jsonPath("$.error.path").value("/api/v1/does-not-exist"));

        // 4xx do client → WARN kèm status + URI, không phải ERROR "Unhandled exception".
        assertThat(output.getOut()).containsPattern("WARN .*Framework error 404 at /api/v1/does-not-exist")
                .doesNotContain("Unhandled exception");
    }

    @Test
    void unsupportedMethodReturns405WithAllowHeader() throws Exception {
        User user = saveUser("Wrong method");
        mockMvc.perform(as(user, put("/api/v1/profile")).content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, containsString("PATCH")))
                .andExpect(jsonPath("$.error.code").value("SYS_002"));
    }

    @Test
    void unsupportedContentTypeReturns415() throws Exception {
        User user = saveUser("Wrong content type");
        mockMvc.perform(as(user, patch("/api/v1/profile")).contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.code").value("SYS_003"));
    }
}
