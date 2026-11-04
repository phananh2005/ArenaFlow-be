package com.phananh.arenaflow.api.profile;

import com.phananh.arenaflow.api.ApiIntegrationTest;

import com.phananh.arenaflow.domain.entity.team.Team;
import com.phananh.arenaflow.domain.entity.team.TeamMember;
import com.phananh.arenaflow.domain.entity.user.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProfileControllerTest extends ApiIntegrationTest {

    private static final String BEN_NGHE_WARD = "10000000-0000-7000-8000-000000000001";
    private static final String HCM_PROVINCE = "00000000-0000-7000-8000-000000000001";

    @Test
    void requiresAccessToken() throws Exception {
        mockMvc.perform(get("/api/v1/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_001"));
    }

    @Test
    void getAndPatchProfile() throws Exception {
        User user = saveUser("Profile API");
        flush();

        mockMvc.perform(as(user, get("/api/v1/profile")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(user.getId().toString()))
                .andExpect(jsonPath("$.data.displayName").value("Profile API"))
                .andExpect(jsonPath("$.data.ward").doesNotExist());

        mockMvc.perform(as(user, patch("/api/v1/profile"))
                        .content("{\"displayName\":\"Renamed\",\"gender\":\"FEMALE\",\"wardId\":\"" + BEN_NGHE_WARD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Renamed"))
                .andExpect(jsonPath("$.data.gender").value("FEMALE"))
                .andExpect(jsonPath("$.data.ward.wardName").value("Phường Bến Nghé"))
                .andExpect(jsonPath("$.data.ward.provinceName").value("TP. Hồ Chí Minh"));
    }

    @Test
    void patchProfileValidation() throws Exception {
        User user = saveUser("Validate API");
        flush();

        mockMvc.perform(as(user, patch("/api/v1/profile")).content("{\"wardId\":\"" + HCM_PROVINCE + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("USER_002"));

        mockMvc.perform(as(user, patch("/api/v1/profile"))
                        .content("{\"dateOfBirth\":\"" + LocalDate.now().minusYears(10) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.validationErrors[0].field").value("dateOfBirth"));
    }

    @Test
    void deleteAccountIsIdempotentThenProfileIsGone() throws Exception {
        User user = saveUser("Delete API");
        flush();

        mockMvc.perform(as(user, delete("/api/v1/profile"))).andExpect(status().isNoContent());
        flush();
        mockMvc.perform(as(user, get("/api/v1/profile")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_001"));
        mockMvc.perform(as(user, delete("/api/v1/profile"))).andExpect(status().isNoContent());
    }

    @Test
    void deleteAccountBlockedForCaptainWithOtherMembers() throws Exception {
        User captain = saveUser("Captain API");
        User member = saveUser("Member API");
        Team team = saveTeam("Team API", captain);
        saveMember(team, member, TeamMember.Role.MEMBER, TeamMember.Status.ACTIVE);
        flush();

        mockMvc.perform(as(captain, delete("/api/v1/profile")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("USER_003"));
    }
}
