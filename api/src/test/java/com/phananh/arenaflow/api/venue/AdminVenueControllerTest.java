package com.phananh.arenaflow.api.venue;

import com.phananh.arenaflow.api.ApiIntegrationTest;
import com.phananh.arenaflow.domain.entity.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Kiểm các API Admin trên địa điểm qua HTTP với DB thật: danh sách toàn bộ địa điểm, ngừng/kích hoạt lại, report. */
class AdminVenueControllerTest extends ApiIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void adminListsAndResolvesReportsWithUnmaskedNamesAndConditionalResolve() throws Exception {
        User reporter = saveUser("Report Lister Reporter");
        User admin = saveUser("Report Lister Admin", User.Role.ADMIN);
        UUID venueId = createVenue(reporter, "Sân danh sách report");
        UUID otherVenueId = createVenue(reporter, "Sân report khác");
        UUID wrongInfo = report(reporter, venueId, "WRONG_INFO");
        UUID closed = report(reporter, venueId, "CLOSED");
        report(reporter, otherVenueId, "OTHER");
        jdbcTemplate.update("UPDATE \"user\" SET deleted_at = (now() AT TIME ZONE 'Asia/Ho_Chi_Minh') WHERE id = ?", reporter.getId());
        flush();

        mockMvc.perform(as(reporter, get("/api/v1/admin/venue-reports")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH_004"));
        mockMvc.perform(as(admin, get("/api/v1/admin/venue-reports")).param("venueId", venueId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].reportId").value(closed.toString()))
                .andExpect(jsonPath("$.data[0].venueName").value("Sân danh sách report"))
                .andExpect(jsonPath("$.data[0].venueStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data[0].reporterName").value("Report Lister Reporter"))
                .andExpect(jsonPath("$.data[0].isReporterDeleted").value(true))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data[0].handledById").doesNotExist())
                .andExpect(jsonPath("$.data[0].isHandledByDeleted").value(false))
                .andExpect(jsonPath("$.data[1].reportId").value(wrongInfo.toString()))
                .andExpect(jsonPath("$.pagination.totalItems").value(2));
        mockMvc.perform(as(admin, get("/api/v1/admin/venue-reports")).param("status", "pending"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mockMvc.perform(as(admin, get("/api/v1/admin/venue-reports")).param("pageSize", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        mockMvc.perform(as(admin, patch("/api/v1/admin/venue-reports/" + wrongInfo + "/resolve"))
                        .content("{\"status\":\"REJECTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.handledById").value(admin.getId().toString()))
                .andExpect(jsonPath("$.data.handledByName").value("Report Lister Admin"))
                .andExpect(jsonPath("$.data.isHandledByDeleted").value(false))
                .andExpect(jsonPath("$.data.handledAt").isString());
        mockMvc.perform(as(admin, get("/api/v1/admin/venue-reports")).param("status", "ALL")
                        .param("venueId", venueId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
        mockMvc.perform(as(admin, patch("/api/v1/admin/venue-reports/" + wrongInfo + "/resolve"))
                        .content("{\"status\":\"PENDING\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mockMvc.perform(as(admin, patch("/api/v1/admin/venue-reports/" + UUID.randomUUID() + "/resolve"))
                        .content("{\"status\":\"VERIFIED\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("VENUE_009"));
        mockMvc.perform(as(admin, patch("/api/v1/admin/venue-reports/" + wrongInfo + "/resolve"))
                        .content("{\"status\":\"VERIFIED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("VENUE_010"));
    }

    @Test
    void adminListsEveryVenueStatusByDefaultWithoutLocationFilter() throws Exception {
        User user = saveUser("Admin List User");
        User admin = saveUser("Admin List Admin", User.Role.ADMIN);
        String token = "AdmList" + UUID.randomUUID().toString().substring(0, 8);
        UUID activeId = createVenue(user, token + " A");
        UUID inactiveId = createVenue(user, token + " B");
        jdbcTemplate.update("UPDATE venue SET status = 'INACTIVE', admin_verified_inactive_at = (now() AT TIME ZONE 'Asia/Ho_Chi_Minh') WHERE id = ?",
                inactiveId);
        flush();

        mockMvc.perform(as(user, get("/api/v1/admin/venues")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH_004"));
        mockMvc.perform(as(admin, get("/api/v1/admin/venues")).param("q", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].venueId").value(activeId.toString()))
                .andExpect(jsonPath("$.data[1].venueId").value(inactiveId.toString()))
                .andExpect(jsonPath("$.data[1].status").value("INACTIVE"))
                .andExpect(jsonPath("$.data[0].distanceKm").doesNotExist());
        mockMvc.perform(as(admin, get("/api/v1/admin/venues")).param("q", token).param("status", "INACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].venueId").value(inactiveId.toString()));
        mockMvc.perform(as(admin, get("/api/v1/admin/venues")).param("pageSize", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.validationErrors[0].field").value("pageSize"));
    }

    @Test
    void adminMarksInactiveVerifyingPendingClosureReportsAndReactivates() throws Exception {
        User creator = saveUser("Inactive Venue Creator");
        User reporter = saveUser("Inactive Venue Reporter");
        User admin = saveUser("Inactive Venue Admin", User.Role.ADMIN);
        UUID venueId = createVenue(creator, "Sân ngừng hoạt động");
        UUID closed = report(reporter, venueId, "CLOSED");
        UUID wrongInfo = report(reporter, venueId, "WRONG_INFO");
        jdbcTemplate.update("UPDATE \"user\" SET deleted_at = (now() AT TIME ZONE 'Asia/Ho_Chi_Minh') WHERE id = ?", creator.getId());
        flush();

        mockMvc.perform(as(reporter, patch("/api/v1/admin/venues/" + venueId + "/mark-inactive")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH_004"));
        mockMvc.perform(as(admin, patch("/api/v1/admin/venues/" + venueId + "/mark-inactive")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.venueId").value(venueId.toString()))
                .andExpect(jsonPath("$.data.status").value("INACTIVE"))
                .andExpect(jsonPath("$.data.adminVerifiedInactiveAt").isString())
                .andExpect(jsonPath("$.data.createdByName").value("Inactive Venue Creator"))
                .andExpect(jsonPath("$.data.isCreatedByDeleted").value(true))
                .andExpect(jsonPath("$.data.images").isArray());
        assertThat(reportStatus(closed)).isEqualTo("VERIFIED");
        assertThat(jdbcTemplate.queryForObject("SELECT handled_by FROM venue_report WHERE id = ?", UUID.class, closed))
                .isEqualTo(admin.getId());
        assertThat(reportStatus(wrongInfo)).isEqualTo("PENDING");

        mockMvc.perform(as(admin, patch("/api/v1/admin/venues/" + venueId + "/reactivate")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.adminVerifiedInactiveAt").doesNotExist());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT closure_alerted_at IS NULL AND stale_alerted_at IS NULL FROM venue WHERE id = ?",
                Boolean.class, venueId)).isTrue();
        assertThat(reportStatus(wrongInfo)).isEqualTo("PENDING");

        mockMvc.perform(as(admin, patch("/api/v1/admin/venues/" + venueId + "/reactivate")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("VENUE_008"));
        mockMvc.perform(as(admin, patch("/api/v1/admin/venues/" + UUID.randomUUID() + "/mark-inactive")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("VENUE_001"));
        mockMvc.perform(as(admin, patch("/api/v1/admin/venues/" + venueId + "/mark-inactive")))
                .andExpect(status().isOk());
        mockMvc.perform(as(admin, patch("/api/v1/admin/venues/" + venueId + "/mark-inactive")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("VENUE_007"));
    }

    private UUID report(User reporter, UUID venueId, String reason) throws Exception {
        String reportId = mockMvc.perform(as(reporter, post("/api/v1/venues/" + venueId + "/reports"))
                        .content("{\"reason\":\"" + reason + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString().replaceAll(".*\"reportId\":\"([^\"]+).*", "$1");
        flush();
        return UUID.fromString(reportId);
    }

    private String reportStatus(UUID reportId) {
        return jdbcTemplate.queryForObject("SELECT status FROM venue_report WHERE id = ?", String.class, reportId);
    }

    private UUID createVenue(User creator, String name) throws Exception {
        UUID provinceId = UUID.randomUUID();
        UUID wardId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO administrative_unit (id, name, level) VALUES (?, 'Admin Province', 'province')", provinceId);
        jdbcTemplate.update("INSERT INTO administrative_unit (id, name, level, parent_id) VALUES (?, 'Admin Ward', 'ward', ?)",
                wardId, provinceId);
        String body = """
                {"venueName":"%s","address":"Địa chỉ %s","latitude":10.0,"longitude":106.0,"wardId":"%s"}
                """.formatted(name, name, wardId);
        String venueId = mockMvc.perform(as(creator, post("/api/v1/venues")).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString().replaceAll(".*\"venueId\":\"([^\"]+).*", "$1");
        flush();
        return UUID.fromString(venueId);
    }
}
