package com.phananh.arenaflow.application.support.storage;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CloudinaryImageUploadSignerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochSecond(1_795_193_200L), ZoneOffset.UTC);
    private static final CloudinaryProperties PROPERTIES = new CloudinaryProperties(
            "demo-cloud", "123456789012345", "abcd", "arenaflow-dev");

    @Test
    void signsConfiguredVenueUploadWithBoundPublicIdAndOneHourExpiry() {
        UUID uploaderId = UUID.fromString("01936a1c-4b2e-7f3a-9c5d-2e8f4a6b1c01");
        ImageUploadSigner signer = new CloudinaryImageUploadSigner(PROPERTIES, CLOCK);

        UploadSignature upload = signer.signUpload("venues/" + uploaderId, uploaderId);

        assertThat(signer.isConfigured()).isTrue();
        assertThat(upload.uploadUrl()).isEqualTo("https://api.cloudinary.com/v1_1/demo-cloud/image/upload");
        assertThat(upload.cloudName()).isEqualTo("demo-cloud");
        assertThat(upload.apiKey()).isEqualTo("123456789012345");
        assertThat(upload.timestamp()).isEqualTo(1_795_193_200L);
        assertThat(upload.allowedFormats()).isEqualTo("jpg,jpeg,png,webp");
        assertThat(upload.overwrite()).isFalse();
        assertThat(upload.expiresAt()).isEqualTo(Instant.ofEpochSecond(1_795_196_800L).atOffset(ZoneOffset.UTC));
        assertThat(upload.publicId()).matches("^arenaflow-dev/venues/01936a1c-4b2e-7f3a-9c5d-2e8f4a6b1c01/\\d{10}-[A-Za-z0-9_-]{22}-[A-Za-z0-9_-]{22}$");
        assertThat(signer.parsePublicId(upload.publicId())).isPresent();
        assertThat(signer.isUploadedBy(signer.parsePublicId(upload.publicId()).orElseThrow(), uploaderId)).isTrue();
        assertThat(signer.isUploadedBy(signer.parsePublicId(upload.publicId()).orElseThrow(), UUID.randomUUID())).isFalse();
    }

    @Test
    void rejectsMalformedOrDifferentScopePublicIdsAndUsesConstantTimeResponseVerification() {
        ImageUploadSigner signer = new CloudinaryImageUploadSigner(PROPERTIES, CLOCK);
        String validPublicId = "arenaflow-dev/venues/01936a1c-4b2e-7f3a-9c5d-2e8f4a6b1c01/1795193200-AbCdEfGhIjKlMn_OpQr-St-AbCdEfGhIjKlMn_OpQr-St";

        assertThat(signer.parsePublicId(validPublicId)).isPresent();
        assertThat(signer.parsePublicId("arenaflow-dev/venues/../other/1795193200-AbCdEfGhIjKlMn_OpQr-St-AbCdEfGhIjKlMn_OpQr-St")).isEmpty();
        assertThat(signer.parsePublicId("other-dev/venues/01936a1c-4b2e-7f3a-9c5d-2e8f4a6b1c01/1795193200-AbCdEfGhIjKlMn_OpQr-St-AbCdEfGhIjKlMn_OpQr-St")).isEmpty();
        assertThat(signer.verifyUploadResponse(validPublicId, 1_795_193_260L,
                "162941fb856be55a3b07160f47e199a4a6895362")).isTrue();
        assertThat(signer.verifyUploadResponse(validPublicId, 1_795_193_260L,
                "062941fb856be55a3b07160f47e199a4a6895362")).isFalse();
    }

    @Test
    void treatsBlankConfigurationAsUnavailable() {
        ImageUploadSigner signer = new CloudinaryImageUploadSigner(
                new CloudinaryProperties("demo-cloud", "key", "", "arenaflow-dev"), CLOCK);

        assertThat(signer.isConfigured()).isFalse();
    }
}
