package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.AddVenueImageRequest;
import com.phananh.arenaflow.application.dto.response.VenueImageResponse;
import com.phananh.arenaflow.application.dto.response.VenueImageUploadSignatureResponse;
import com.phananh.arenaflow.application.mapper.VenueMapper;
import com.phananh.arenaflow.application.service.impl.VenueImageServiceImpl;
import com.phananh.arenaflow.application.support.VenueGuard;
import com.phananh.arenaflow.application.support.security.CurrentActor;
import com.phananh.arenaflow.application.support.storage.ImageUploadSigner;
import com.phananh.arenaflow.application.support.storage.SignedPublicId;
import com.phananh.arenaflow.application.support.venue.VenueImageCleanupProperties;
import com.phananh.arenaflow.application.support.storage.UploadSignature;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.entity.venue.Venue;
import com.phananh.arenaflow.domain.entity.venue.VenueImage;
import com.phananh.arenaflow.domain.projection.VenueImageStatsView;
import com.phananh.arenaflow.domain.repository.VenueImageRepository;
import com.phananh.arenaflow.domain.repository.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.mapstruct.factory.Mappers;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class VenueImageServiceImplTest {

    private final VenueRepository venueRepository = mock(VenueRepository.class);
    private final VenueImageRepository imageRepository = mock(VenueImageRepository.class);
    private final ImageUploadSigner signer = mock(ImageUploadSigner.class);
    private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
    private final UUID venueId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private VenueImageService service;

    @BeforeEach
    void setUp() {
        service = new VenueImageServiceImpl(new VenueGuard(venueRepository), imageRepository, signer,
                Mappers.getMapper(VenueMapper.class), transactionTemplate,
                new VenueImageCleanupProperties(Duration.ofHours(24), Duration.ofHours(6), Duration.ofMinutes(5)),
                Clock.fixed(Instant.ofEpochSecond(1_795_193_200L), ZoneOffset.UTC));
        when(transactionTemplate.execute(any(TransactionCallback.class))).thenAnswer(invocation ->
                ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null));
    }

    @Test
    void issuesUploadSignatureOnlyForWritableVenueBelowImageLimit() {
        Venue venue = Venue.builder().id(venueId).status(Venue.Status.ACTIVE).build();
        UploadSignature signature = new UploadSignature("upload-url", "cloud", "api-key", "public-id", 123L,
                "jpg,jpeg,png,webp", false, "signature", OffsetDateTime.parse("2026-11-18T00:00:00Z"));
        when(signer.isConfigured()).thenReturn(true);
        when(venueRepository.findById(venueId)).thenReturn(java.util.Optional.of(venue));
        when(imageRepository.countByVenueId(venueId)).thenReturn(4L);
        when(signer.signUpload(eq("venues/" + venueId), eq(actorId))).thenReturn(signature);

        VenueImageUploadSignatureResponse response = service.issueUploadSignature(venueId, new CurrentActor(actorId, false));

        assertThat(response.publicId()).isEqualTo("public-id");
        assertThat(response.allowedFormats()).isEqualTo("jpg,jpeg,png,webp");
        verify(signer).signUpload("venues/" + venueId, actorId);
    }

    @Test
    void storesVerifiedFirstImageAsPrimaryInsideLockedTransaction() {
        String publicId = "arenaflow-dev/venues/" + venueId + "/1795193200-AbCdEfGhIjKlMn_OpQr-St-AbCdEfGhIjKlMn_OpQr-St";
        SignedPublicId signedId = new SignedPublicId("venues/" + venueId, Instant.ofEpochSecond(1_795_193_200L),
                "AbCdEfGhIjKlMn_OpQr-St", "AbCdEfGhIjKlMn_OpQr-St");
        VenueImageStatsView stats = mock(VenueImageStatsView.class);
        when(signer.isConfigured()).thenReturn(true);
        when(signer.parsePublicId(publicId)).thenReturn(java.util.Optional.of(signedId));
        when(signer.isUploadedBy(signedId, actorId)).thenReturn(true);
        when(signer.verifyUploadResponse(publicId, 1L, "response-signature")).thenReturn(true);
        when(venueRepository.lockRow(venueId)).thenReturn(1);
        when(venueRepository.findById(venueId)).thenReturn(java.util.Optional.of(
                Venue.builder().id(venueId).status(Venue.Status.ACTIVE).build()));
        when(imageRepository.findStatsByVenueId(venueId)).thenReturn(stats);
        when(stats.getImageCount()).thenReturn(0L);
        when(stats.getMaxDisplayOrder()).thenReturn(0);
        when(signer.deliveryUrl(publicId, 1L, "png")).thenReturn("delivery-url");
        when(imageRepository.saveAndFlush(any(VenueImage.class))).thenAnswer(invocation -> {
            VenueImage image = invocation.getArgument(0);
            ReflectionTestUtils.setField(image, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(image, "createdAt", OffsetDateTime.parse("2026-11-18T00:00:00Z"));
            return image;
        });

        VenueImageResponse response = service.addImage(venueId, new CurrentActor(actorId, false),
                new AddVenueImageRequest(publicId, 1L, "png", "response-signature", false));

        assertThat(response.venueId()).isEqualTo(venueId);
        assertThat(response.imageUrl()).isEqualTo("delivery-url");
        assertThat(response.isPrimary()).isTrue();
        assertThat(response.displayOrder()).isEqualTo(1);
        verify(venueRepository).lockRow(venueId);
        verify(imageRepository).saveAndFlush(any(VenueImage.class));
    }

    @Test
    void clearsExistingPrimaryBeforeAddingRequestedPrimaryAndMapsDuplicateToVenue006() {
        String publicId = "arenaflow-dev/venues/" + venueId + "/1795193200-AbCdEfGhIjKlMn_OpQr-St-AbCdEfGhIjKlMn_OpQr-St";
        SignedPublicId signedId = new SignedPublicId("venues/" + venueId, Instant.ofEpochSecond(1_795_193_200L),
                "AbCdEfGhIjKlMn_OpQr-St", "AbCdEfGhIjKlMn_OpQr-St");
        VenueImageStatsView stats = mock(VenueImageStatsView.class);
        when(signer.isConfigured()).thenReturn(true);
        when(signer.parsePublicId(publicId)).thenReturn(java.util.Optional.of(signedId));
        when(signer.isUploadedBy(signedId, actorId)).thenReturn(true);
        when(signer.verifyUploadResponse(publicId, 1L, "response-signature")).thenReturn(true);
        when(venueRepository.lockRow(venueId)).thenReturn(1);
        when(venueRepository.findById(venueId)).thenReturn(java.util.Optional.of(
                Venue.builder().id(venueId).status(Venue.Status.ACTIVE).build()));
        when(imageRepository.findStatsByVenueId(venueId)).thenReturn(stats);
        when(stats.getImageCount()).thenReturn(1L);
        when(stats.getMaxDisplayOrder()).thenReturn(3);
        when(signer.deliveryUrl(publicId, 1L, "png")).thenReturn("delivery-url");
        when(imageRepository.saveAndFlush(any(VenueImage.class))).thenThrow(new DataIntegrityViolationException("uq_venue_image_public_id"));

        assertError(() -> service.addImage(venueId, new CurrentActor(actorId, false),
                new AddVenueImageRequest(publicId, 1L, "png", "response-signature", true)), ErrorCode.VENUE_006);

        verify(imageRepository).clearPrimary(venueId);
    }

    @Test
    void rejectsExpiredOrTooFarFutureSignedUploadBeforeOpeningDatabaseTransaction() {
        when(signer.isConfigured()).thenReturn(true);
        for (Instant signedAt : java.util.List.of(Instant.ofEpochSecond(1_795_171_599L),
                Instant.ofEpochSecond(1_795_193_501L))) {
            String publicId = "arenaflow-dev/venues/" + venueId + "/" + signedAt.getEpochSecond()
                    + "-AbCdEfGhIjKlMn_OpQr-St-AbCdEfGhIjKlMn_OpQr-St";
            when(signer.parsePublicId(publicId)).thenReturn(java.util.Optional.of(
                    new SignedPublicId("venues/" + venueId, signedAt, "AbCdEfGhIjKlMn_OpQr-St", "AbCdEfGhIjKlMn_OpQr-St")));
            assertError(() -> service.addImage(venueId, new CurrentActor(actorId, false),
                    new AddVenueImageRequest(publicId, 1L, "png", "response-signature", false)), ErrorCode.VENUE_005);
        }
        verifyNoInteractions(venueRepository, imageRepository);
    }

    @Test
    void rejectsUnverifiedUploadBeforeOpeningDatabaseTransaction() {
        when(signer.isConfigured()).thenReturn(true);
        when(signer.parsePublicId("invalid-public-id")).thenReturn(java.util.Optional.empty());

        assertError(() -> service.addImage(venueId, new CurrentActor(actorId, false),
                new AddVenueImageRequest("invalid-public-id", 1L, "png", "response-signature", false)), ErrorCode.VENUE_005);
        verifyNoInteractions(venueRepository, imageRepository);
    }

    @Test
    void returnsVenue012BeforeAnyDatabaseAccessWhenStorageIsUnavailable() {
        when(signer.isConfigured()).thenReturn(false);

        assertError(() -> service.issueUploadSignature(venueId, new CurrentActor(actorId, false)), ErrorCode.VENUE_012);
        verifyNoInteractions(venueRepository, imageRepository);
    }

    @Test
    void returnsVenue002BeforeCountingForInactiveVenueCalledByNonAdmin() {
        when(signer.isConfigured()).thenReturn(true);
        when(venueRepository.findById(venueId)).thenReturn(java.util.Optional.of(
                Venue.builder().id(venueId).status(Venue.Status.INACTIVE).build()));

        assertError(() -> service.issueUploadSignature(venueId, new CurrentActor(actorId, false)), ErrorCode.VENUE_002);
        verifyNoInteractions(imageRepository);
    }

    @Test
    void returnsVenue003AtFiveImagesWithoutCreatingSignature() {
        when(signer.isConfigured()).thenReturn(true);
        when(venueRepository.findById(venueId)).thenReturn(java.util.Optional.of(
                Venue.builder().id(venueId).status(Venue.Status.ACTIVE).build()));
        when(imageRepository.countByVenueId(venueId)).thenReturn(5L);

        assertError(() -> service.issueUploadSignature(venueId, new CurrentActor(actorId, false)), ErrorCode.VENUE_003);
        verify(signer, org.mockito.Mockito.never()).signUpload(any(), any());
    }

    private static void assertError(Runnable operation, ErrorCode expected) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
