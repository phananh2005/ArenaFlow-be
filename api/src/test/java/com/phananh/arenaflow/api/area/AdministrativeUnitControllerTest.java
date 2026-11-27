package com.phananh.arenaflow.api.area;

import com.phananh.arenaflow.api.ApiIntegrationTest;
import com.phananh.arenaflow.domain.entity.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Danh mục tỉnh/thành, phường/xã (đầu-cuối trên DB dev). */
class AdministrativeUnitControllerTest extends ApiIntegrationTest {

    private static final String HCM_PROVINCE = "00000000-0000-7000-8000-000000000001";
    private static final String BEN_NGHE_WARD = "10000000-0000-7000-8000-000000000001";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void listsProvincesWithoutPagination() throws Exception {
        User user = saveUser("Area User");
        flush();

        mockMvc.perform(as(user, get("/api/v1/provinces")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination").doesNotExist())
                .andExpect(jsonPath("$.data[*].provinceName", hasItem("Đà Nẵng")))
                .andExpect(jsonPath("$.data[*].provinceId", not(hasItem(BEN_NGHE_WARD))));
    }

    @Test
    void listsWardsOfProvinceAndRejectsWardOrUnknownId() throws Exception {
        User user = saveUser("Area User");
        flush();

        mockMvc.perform(as(user, get("/api/v1/provinces/{provinceId}/wards", HCM_PROVINCE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination").doesNotExist())
                .andExpect(jsonPath("$.data[*].wardName", hasItem("Phường Bến Nghé")))
                .andExpect(jsonPath("$.data[*].provinceId").doesNotExist());

        mockMvc.perform(as(user, get("/api/v1/provinces/{provinceId}/wards", BEN_NGHE_WARD)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_004"));
        mockMvc.perform(as(user, get("/api/v1/provinces/{provinceId}/wards", UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_004"));
        mockMvc.perform(as(user, get("/api/v1/provinces/{provinceId}/wards", "not-a-uuid")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void provinceWithoutWardsReturnsEmptyList() throws Exception {
        User user = saveUser("Area User");
        flush();
        UUID emptyProvince = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO administrative_unit (id, name, level) VALUES (?, 'Tỉnh Rỗng', 'province')",
                emptyProvince);

        mockMvc.perform(as(user, get("/api/v1/provinces/{provinceId}/wards", emptyProvince)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/provinces"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_001"));
        mockMvc.perform(get("/api/v1/provinces/{provinceId}/wards", HCM_PROVINCE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void provinceListIsNotRateLimited() throws Exception {
        User user = saveUser("Area Heavy User");
        flush();

        for (int i = 0; i < 35; i++) {
            mockMvc.perform(as(user, get("/api/v1/provinces"))).andExpect(status().isOk());
        }
        mockMvc.perform(as(user, get("/api/v1/provinces/{provinceId}/wards", HCM_PROVINCE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].wardId", everyItem(not(HCM_PROVINCE))));
    }
}
