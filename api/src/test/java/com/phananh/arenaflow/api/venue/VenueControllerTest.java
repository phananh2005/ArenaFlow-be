package com.phananh.arenaflow.api.venue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phananh.arenaflow.api.ApiIntegrationTest;
import com.phananh.arenaflow.domain.entity.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Base64;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VenueControllerTest extends ApiIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void listsVenuesWithPaginationSearchAndGeoValidation() throws Exception {
        User creator = saveUser("Venue Lister");
        UUID wardId = insertWard();
        String body = """
                {"venueName":"Sân Hoa Lư","address":"2 Đinh Tiên Hoàng","latitude":10.7881234,
                 "longitude":106.6991234,"wardId":"%s"}
                """.formatted(wardId);
        mockMvc.perform(as(creator, post("/api/v1/venues")).content(body)).andExpect(status().isCreated());
        flush();

        mockMvc.perform(as(creator, get("/api/v1/venues")).param("q", "hoa lư").param("pageSize", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].venueName").value("Sân Hoa Lư"))
                .andExpect(jsonPath("$.data[0].distanceKm").doesNotExist())
                .andExpect(jsonPath("$.pagination.page").value(1))
                .andExpect(jsonPath("$.pagination.pageSize").value(1));
        mockMvc.perform(as(creator, get("/api/v1/venues")).param("lat", "10.78").param("lng", "106.70"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].distanceKm").isNumber());
        mockMvc.perform(as(creator, get("/api/v1/venues")).param("lat", "10.78"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.validationErrors[0].field").value("lat"));
        mockMvc.perform(as(creator, get("/api/v1/venues")).param("pageSize", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.validationErrors[0].field").value("pageSize"));
    }

    @Test
    void searchKeywordMatchesLikeWildcardsLiterally() throws Exception {
        User creator = saveUser("Venue Wildcard Searcher");
        String token = "Esc" + UUID.randomUUID().toString().substring(0, 8);
        createVenue(creator, token + "%x");
        createVenue(creator, token + "9x");

        mockMvc.perform(as(creator, get("/api/v1/venues")).param("q", token + "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].venueName").value(token + "%x"));
        mockMvc.perform(as(creator, get("/api/v1/venues")).param("q", token + "_x"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void updatesVenueOnlyForOwnerOrAdmin() throws Exception {
        User owner = saveUser("Venue Owner");
        User stranger = saveUser("Venue Stranger");
        User admin = saveUser("Venue Admin", User.Role.ADMIN);
        UUID wardId = insertWard();
        String body = """
                {"venueName":"Sân cũ","address":"Địa chỉ cũ","latitude":10.0,"longitude":106.0,"wardId":"%s"}
                """.formatted(wardId);
        String venueId = mockMvc.perform(as(owner, post("/api/v1/venues")).content(body))
                .andReturn().getResponse().getContentAsString().replaceAll(".*\"venueId\":\"([^\"]+).*", "$1");
        flush();

        mockMvc.perform(as(stranger, patch("/api/v1/venues/" + venueId)).content("{\"venueName\":\"Không được\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("VENUE_013"));
        mockMvc.perform(as(owner, patch("/api/v1/venues/" + venueId)).content("{\"venueName\":\"Sân mới\",\"phone\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.venueName").value("Sân mới"))
                .andExpect(jsonPath("$.data.phone").doesNotExist());
        mockMvc.perform(as(admin, patch("/api/v1/venues/" + venueId)).content("{\"address\":\"Địa chỉ admin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.address").value("Địa chỉ admin"));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "CLOUDINARY_API_SECRET", matches = ".+")
    void persistsCloudinaryUploadedImageThroughSignedDirectUploadFlow() throws Exception {
        User creator = saveUser("Venue Cloudinary Integration");
        UUID wardId = insertWard();
        String createBody = """
                {"venueName":"Sân Cloudinary","address":"Địa chỉ Cloudinary","latitude":10.0,"longitude":106.0,"wardId":"%s"}
                """.formatted(wardId);
        String venueId = mockMvc.perform(as(creator, post("/api/v1/venues")).content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString().replaceAll(".*\"venueId\":\"([^\"]+).*", "$1");
        flush();

        JsonNode upload = new ObjectMapper().readTree(mockMvc.perform(as(creator,
                        post("/api/v1/venues/" + venueId + "/image-upload-signatures")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("data");
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLxEQAAAABJRU5ErkJggg==")) {
            @Override public String getFilename() { return "integration.png"; }
        });
        form.add("api_key", upload.path("apiKey").asText());
        form.add("timestamp", upload.path("timestamp").asText());
        form.add("public_id", upload.path("publicId").asText());
        form.add("allowed_formats", upload.path("allowedFormats").asText());
        form.add("overwrite", Boolean.toString(upload.path("overwrite").asBoolean()));
        form.add("signature", upload.path("signature").asText());
        String cloudinaryResponse = RestClient.create().post().uri(upload.path("uploadUrl").asText())
                .contentType(MediaType.MULTIPART_FORM_DATA).body(form).retrieve().body(String.class);
        JsonNode cloudinary = new ObjectMapper().readTree(cloudinaryResponse);

        String persistedBody = """
                {"publicId":"%s","version":%d,"format":"%s","signature":"%s","isPrimary":true}
                """.formatted(cloudinary.path("public_id").asText(), cloudinary.path("version").asLong(),
                cloudinary.path("format").asText(), cloudinary.path("signature").asText());
        mockMvc.perform(as(creator, post("/api/v1/venues/" + venueId + "/images")).content(persistedBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.venueId").value(venueId))
                .andExpect(jsonPath("$.data.isPrimary").value(true))
                .andExpect(jsonPath("$.data.imageUrl").isString());
    }

    @Test
    void rejectsUnverifiedCloudinaryImageResultWithoutPersistingImage() throws Exception {
        User creator = saveUser("Venue Image Result Sender");
        UUID wardId = insertWard();
        String body = """
                {"venueName":"Sân lưu ảnh","address":"Địa chỉ lưu ảnh","latitude":10.0,"longitude":106.0,"wardId":"%s"}
                """.formatted(wardId);
        String venueId = mockMvc.perform(as(creator, post("/api/v1/venues")).content(body))
                .andReturn().getResponse().getContentAsString().replaceAll(".*\"venueId\":\"([^\"]+).*", "$1");
        flush();

        mockMvc.perform(as(creator, post("/api/v1/venues/" + venueId + "/images"))
                .content("{\"publicId\":\"forged\",\"version\":1,\"format\":\"png\",\"signature\":\"forged\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VENUE_005"));
    }

    @Test
    void issuesCloudinaryUploadSignatureForVenueImageWithoutExposingSecret() throws Exception {
        User creator = saveUser("Venue Image Signer");
        UUID wardId = insertWard();
        String body = """
                {"venueName":"Sân ký ảnh","address":"Địa chỉ ký ảnh","latitude":10.0,"longitude":106.0,"wardId":"%s"}
                """.formatted(wardId);
        String venueId = mockMvc.perform(as(creator, post("/api/v1/venues")).content(body))
                .andReturn().getResponse().getContentAsString().replaceAll(".*\"venueId\":\"([^\"]+).*", "$1");
        flush();

        mockMvc.perform(as(creator, post("/api/v1/venues/" + venueId + "/image-upload-signatures"))
                .content(""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publicId").value(org.hamcrest.Matchers.startsWith("arenaflow-dev/venues/" + venueId + "/")))
                .andExpect(jsonPath("$.data.allowedFormats").value("jpg,jpeg,png,webp"))
                .andExpect(jsonPath("$.data.overwrite").value(false))
                .andExpect(jsonPath("$.data.apiSecret").doesNotExist());
    }

    @Test
    void createsVenueAndReturnsDetailOrValidationAndNotFoundErrors() throws Exception {
        User creator = saveUser("Venue Creator", User.Role.ADMIN);
        UUID wardId = insertWard();
        flush();
        String body = """
                {"venueName":"Sân Hoa Lư","address":"2 Đinh Tiên Hoàng","latitude":10.7881234,
                 "longitude":106.6991234,"wardId":"%s","venueType":"SEVEN_A_SIDE"}
                """.formatted(wardId);

        String venueId = mockMvc.perform(as(creator, post("/api/v1/venues")).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.data.venueName").value("Sân Hoa Lư"))
                .andExpect(jsonPath("$.data.createdByType").value("ADMIN"))
                .andExpect(jsonPath("$.data.createdByName").value("Venue Creator"))
                .andExpect(jsonPath("$.data.ward.wardId").value(wardId.toString()))
                .andExpect(jsonPath("$.data.images").isEmpty())
                .andReturn().getResponse().getContentAsString().replaceAll(".*\"venueId\":\"([^\"]+).*", "$1");

        mockMvc.perform(as(creator, get("/api/v1/venues/" + venueId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.venueId").value(venueId))
                .andExpect(jsonPath("$.data.ward.wardName").value("Ward Test"));
        mockMvc.perform(as(creator, get("/api/v1/venues/" + UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("VENUE_001"));
        mockMvc.perform(as(creator, post("/api/v1/venues")).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void setsPrimaryImageAndReturnsAllImagesWithInactiveVenueRestrictedToAdmin() throws Exception {
        User creator = saveUser("Venue Primary Setter");
        User admin = saveUser("Venue Primary Admin", User.Role.ADMIN);
        UUID venueId = createVenue(creator, "Sân ảnh chính");
        UUID otherVenueId = createVenue(creator, "Sân khác");
        UUID first = insertImage(venueId, creator, true, 1);
        UUID second = insertImage(venueId, creator, false, 2);
        UUID foreign = insertImage(otherVenueId, creator, true, 1);
        flush();

        mockMvc.perform(as(creator, patch("/api/v1/venues/" + venueId + "/images/" + second + "/set-primary")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].imageId").value(first.toString()))
                .andExpect(jsonPath("$.data[0].isPrimary").value(false))
                .andExpect(jsonPath("$.data[1].imageId").value(second.toString()))
                .andExpect(jsonPath("$.data[1].isPrimary").value(true))
                .andExpect(jsonPath("$.data[1].publicId").doesNotExist());
        mockMvc.perform(as(creator, patch("/api/v1/venues/" + venueId + "/images/" + second + "/set-primary")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[1].isPrimary").value(true));
        assertPrimaryCount(venueId, 1);

        jdbcTemplate.update("UPDATE venue SET status = 'INACTIVE', admin_verified_inactive_at = NOW() WHERE id = ?", venueId);
        flush();
        mockMvc.perform(as(creator, patch("/api/v1/venues/" + venueId + "/images/" + first + "/set-primary")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VENUE_002"));
        mockMvc.perform(as(admin, patch("/api/v1/venues/" + venueId + "/images/" + first + "/set-primary")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].isPrimary").value(true))
                .andExpect(jsonPath("$.data[1].isPrimary").value(false));
        assertPrimaryCount(venueId, 1);

        // Nhánh lỗi để cuối: transaction test dùng chung không hoàn tác clearPrimary của service khi rollback;
        // rollback trên dữ liệu đã commit do VenueImageWriteRaceTest kiểm.
        mockMvc.perform(as(admin, patch("/api/v1/venues/" + venueId + "/images/" + foreign + "/set-primary")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("VENUE_004"));
        mockMvc.perform(as(admin, patch("/api/v1/venues/" + UUID.randomUUID() + "/images/" + second + "/set-primary")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("VENUE_001"));
    }

    @Test
    void deletesImagePromotingFirstRemainingAndEnforcesOwnerAdminAndInactiveRules() throws Exception {
        User creator = saveUser("Venue Image Deleter");
        User stranger = saveUser("Venue Image Stranger");
        User admin = saveUser("Venue Image Delete Admin", User.Role.ADMIN);
        UUID venueId = createVenue(creator, "Sân xoá ảnh");
        String base = UUID.randomUUID().toString().substring(0, 35);
        UUID primary = insertImage(venueId, creator, UUID.randomUUID(), true, 1);
        UUID tieLow = insertImage(venueId, creator, UUID.fromString(base + "1"), false, 3);
        UUID tieHigh = insertImage(venueId, creator, UUID.fromString(base + "2"), false, 3);
        flush();

        mockMvc.perform(as(stranger, delete("/api/v1/venues/" + venueId + "/images/" + primary)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("VENUE_013"));
        mockMvc.perform(as(creator, delete("/api/v1/venues/" + venueId + "/images/" + primary)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[?(@.imageId == '%s')].isPrimary".formatted(tieLow)).value(true))
                .andExpect(jsonPath("$.data[?(@.imageId == '%s')].isPrimary".formatted(tieHigh)).value(false))
                .andExpect(jsonPath("$.data[0].publicId").doesNotExist());
        assertPrimaryCount(venueId, 1);

        jdbcTemplate.update("UPDATE venue SET status = 'INACTIVE', admin_verified_inactive_at = NOW() WHERE id = ?", venueId);
        flush();
        mockMvc.perform(as(creator, delete("/api/v1/venues/" + venueId + "/images/" + tieHigh)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VENUE_002"));
        mockMvc.perform(as(admin, delete("/api/v1/venues/" + venueId + "/images/" + tieHigh)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].imageId").value(tieLow.toString()))
                .andExpect(jsonPath("$.data[0].isPrimary").value(true));
        mockMvc.perform(as(admin, delete("/api/v1/venues/" + venueId + "/images/" + tieLow)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(as(admin, delete("/api/v1/venues/" + venueId + "/images/" + tieLow)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("VENUE_004"));
        mockMvc.perform(as(admin, delete("/api/v1/venues/" + UUID.randomUUID() + "/images/" + tieLow)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("VENUE_001"));
    }

    @Test
    void reportsVenueForNonAdminOnActiveVenueAndRejectsDuplicatePending() throws Exception {
        User reporter = saveUser("Venue Reporter");
        User admin = saveUser("Venue Report Admin", User.Role.ADMIN);
        UUID venueId = createVenue(reporter, "Sân report");
        UUID inactiveVenueId = createVenue(reporter, "Sân đã đóng");
        jdbcTemplate.update("UPDATE venue SET status = 'INACTIVE', admin_verified_inactive_at = NOW() WHERE id = ?",
                inactiveVenueId);
        flush();
        String closed = "{\"reason\":\"CLOSED\",\"detail\":\"Sân đã đóng cửa\"}";

        mockMvc.perform(as(admin, post("/api/v1/venues/" + venueId + "/reports")).content(closed))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("VENUE_015"));
        mockMvc.perform(as(reporter, post("/api/v1/venues/" + venueId + "/reports")).content("{\"reason\":\"closed\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.validationErrors[0].field").value("reason"));
        mockMvc.perform(as(reporter, post("/api/v1/venues/" + venueId + "/reports"))
                        .content("{\"reason\":\"OTHER\",\"detail\":\"%s\"}".formatted("x".repeat(1001))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.validationErrors[0].field").value("detail"));
        mockMvc.perform(as(reporter, post("/api/v1/venues/" + UUID.randomUUID() + "/reports")).content(closed))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("VENUE_001"));
        mockMvc.perform(as(reporter, post("/api/v1/venues/" + inactiveVenueId + "/reports")).content(closed))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VENUE_002"));

        mockMvc.perform(as(reporter, post("/api/v1/venues/" + venueId + "/reports")).content(closed))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.data.reportId").isString())
                .andExpect(jsonPath("$.data.venueId").value(venueId.toString()))
                .andExpect(jsonPath("$.data.reason").value("CLOSED"))
                .andExpect(jsonPath("$.data.detail").value("Sân đã đóng cửa"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.createdAt").isString());
        mockMvc.perform(as(reporter, post("/api/v1/venues/" + venueId + "/reports")).content("{\"reason\":\"WRONG_INFO\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.detail").doesNotExist());
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "SELECT closure_alerted_at IS NOT NULL FROM venue WHERE id = ?", Boolean.class, venueId)).isTrue();

        // Để cuối: vi phạm constraint của bản trùng đưa transaction test dùng chung vào trạng thái aborted
        // (mọi lệnh sau đều lỗi).
        mockMvc.perform(as(reporter, post("/api/v1/venues/" + venueId + "/reports")).content(closed))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("VENUE_014"));
    }

    private UUID createVenue(User creator, String name) throws Exception {
        String body = """
                {"venueName":"%s","address":"Địa chỉ %s","latitude":10.0,"longitude":106.0,"wardId":"%s"}
                """.formatted(name, name, insertWard());
        String venueId = mockMvc.perform(as(creator, post("/api/v1/venues")).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString().replaceAll(".*\"venueId\":\"([^\"]+).*", "$1");
        flush();
        return UUID.fromString(venueId);
    }

    private UUID insertImage(UUID venueId, User uploader, boolean primary, int displayOrder) {
        return insertImage(venueId, uploader, UUID.randomUUID(), primary, displayOrder);
    }

    private UUID insertImage(UUID venueId, User uploader, UUID imageId, boolean primary, int displayOrder) {
        jdbcTemplate.update("INSERT INTO venue_image (id, venue_id, image_url, public_id, is_primary, display_order, uploaded_by) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)", imageId, venueId, "https://img.test/" + imageId,
                "test/venues/" + imageId, primary, displayOrder, uploader.getId());
        return imageId;
    }

    private void assertPrimaryCount(UUID venueId, int expected) {
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM venue_image WHERE venue_id = ? AND is_primary", Integer.class, venueId))
                .isEqualTo(expected);
    }

    private UUID insertWard() {
        UUID provinceId = UUID.randomUUID();
        UUID wardId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO administrative_unit (id, name, level) VALUES (?, 'Province Test', 'province')", provinceId);
        jdbcTemplate.update("INSERT INTO administrative_unit (id, name, level, parent_id) VALUES (?, 'Ward Test', 'ward', ?)", wardId, provinceId);
        return wardId;
    }
}
