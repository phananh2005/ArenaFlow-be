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

    private UUID insertWard() {
        UUID provinceId = UUID.randomUUID();
        UUID wardId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO administrative_unit (id, name, level) VALUES (?, 'Province Test', 'province')", provinceId);
        jdbcTemplate.update("INSERT INTO administrative_unit (id, name, level, parent_id) VALUES (?, 'Ward Test', 'ward', ?)", wardId, provinceId);
        return wardId;
    }
}
