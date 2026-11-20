package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.CreateVenueRequest;
import com.phananh.arenaflow.application.support.storage.AssetPage;
import com.phananh.arenaflow.application.support.storage.ImageAssetAdmin;
import com.phananh.arenaflow.application.support.storage.ImageUploadSigner;
import com.phananh.arenaflow.domain.entity.user.User;
import com.phananh.arenaflow.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Signer thật (quy tắc folder) và tra cứu database thật; chỉ thay thế Cloudinary Admin API. */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "CLOUDINARY_API_SECRET", matches = ".+")
class VenueImageCleanupIntegrationTest {

    @Autowired private VenueImageCleanupService cleanupService;
    @Autowired private ImageUploadSigner imageUploadSigner;
    @Autowired private VenueService venueService;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;
    @MockitoBean private ImageAssetAdmin assetAdmin;

    @Test
    void deletesOnlyExpiredAssetsWithoutVenueImageRowsUnderOwnFolder() {
        UUID venueId = venue();
        String prefix = imageUploadSigner.rootPrefix("venues");
        String oldOrphan = publicId(prefix, venueId, Instant.now().minus(Duration.ofDays(2)));
        String oldReferenced = publicId(prefix, venueId, Instant.now().minus(Duration.ofDays(3)));
        String young = publicId(prefix, venueId, Instant.now().minus(Duration.ofHours(2)));
        String foreignFolder = "other-env/venues/" + venueId + "/" + Instant.now().minus(Duration.ofDays(2)).getEpochSecond()
                + "-AbCdEfGhIjKlMnOpQrStUv-AbCdEfGhIjKlMnOpQrStUv";
        jdbcTemplate.update("INSERT INTO venue_image (id, venue_id, image_url, public_id, is_primary, display_order) "
                + "VALUES (?, ?, 'https://img.test/ref', ?, TRUE, 1)", UUID.randomUUID(), venueId, oldReferenced);
        when(assetAdmin.listAssets(eq(prefix), isNull(), anyInt()))
                .thenReturn(new AssetPage(List.of(oldOrphan, oldReferenced, young, foreignFolder), null));
        when(assetAdmin.deleteAssets(anyList())).thenReturn(Set.of(oldOrphan));

        assertThat(cleanupService.cleanupOrphanAssets()).isEqualTo(1);

        verify(assetAdmin).deleteAssets(List.of(oldOrphan));
    }

    private static String publicId(String prefix, UUID venueId, Instant signedAt) {
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 22);
        return prefix + venueId + "/" + signedAt.getEpochSecond() + "-" + random + "-" + random;
    }

    private UUID venue() {
        User creator = userRepository.saveAndFlush(User.builder().displayName("Cleanup Creator").build());
        UUID provinceId = UUID.randomUUID();
        UUID wardId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO administrative_unit (id, name, level) VALUES (?, 'Cleanup Province', 'province')",
                provinceId);
        jdbcTemplate.update("INSERT INTO administrative_unit (id, name, level, parent_id) VALUES (?, 'Cleanup Ward', 'ward', ?)",
                wardId, provinceId);
        UUID venueId = venueService.createVenue(creator.getId(), new CreateVenueRequest("Cleanup Venue", "Address",
                new BigDecimal("10.1"), new BigDecimal("106.1"), wardId, null, null, null)).venueId();
        entityManager.flush();
        return venueId;
    }
}
