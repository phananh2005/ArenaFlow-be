package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.AddVenueImageRequest;
import com.phananh.arenaflow.application.dto.response.VenueImageResponse;
import com.phananh.arenaflow.application.dto.response.VenueImageUploadSignatureResponse;
import com.phananh.arenaflow.application.mapper.VenueMapper;
import com.phananh.arenaflow.application.service.impl.VenueImageServiceImpl;
import com.phananh.arenaflow.application.support.AdminAuditLog;
import com.phananh.arenaflow.application.support.DataIntegrityViolationFixtures;
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
    private final AdminAuditLog auditLog = mock(AdminAuditLog.class);
    private final UUID venueId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private VenueImageService service;

    @BeforeEach
    void setUp() {
        service = new VenueImageServiceImpl(new VenueGuard(venueRepository), imageRepository, signer,
                Mappers.getMapper(VenueMapper.class), transactionTemplate,
                new VenueImageCleanupProperties(Duration.ofHours(24), Duration.ofHours(6), Duration.ofMinutes(5),
                        null, null, null, null),
                auditLog, Clock.fixed(Instant.ofEpochSecond(1_795_193_200L), ZoneOffset.UTC));
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
        String publicId = stubConfirmablePrimaryImage();
        when(imageRepository.saveAndFlush(any(VenueImage.class)))
                .thenThrow(DataIntegrityViolationFixtures.violation("uq_venue_image_public_id"));

        assertError(() -> service.addImage(venueId, new CurrentActor(actorId, false),
                new AddVenueImageRequest(publicId, 1L, "png", "response-signature", true)), ErrorCode.VENUE_006);

        verify(imageRepository).clearPrimary(venueId);
    }

    @Test
    void otherIntegrityViolationOnAddImageIsRethrownNotMappedToVenue006() {
        String publicId = stubConfirmablePrimaryImage();
        DataIntegrityViolationException fkViolation = DataIntegrityViolationFixtures.violation("venue_image_venue_id_fkey");
        when(imageRepository.saveAndFlush(any(VenueImage.class))).thenThrow(fkViolation);

        assertThatThrownBy(() -> service.addImage(venueId, new CurrentActor(actorId, false),
                new AddVenueImageRequest(publicId, 1L, "png", "response-signature", true))).isSameAs(fkViolation);
    }

    /** Upload có chữ ký qua mọi kiểm tra cục bộ, trên địa điểm ACTIVE đã có một ảnh. */
    private String stubConfirmablePrimaryImage() {
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
        return publicId;
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

    @Test
    void setsPrimaryImageAfterClearingOldPrimaryAndReturnsAllImagesInDisplayOrder() {
        UUID imageId = UUID.randomUUID();
        lockedVenue(Venue.Status.ACTIVE);
        when(imageRepository.setPrimary(venueId, imageId)).thenReturn(1);
        when(imageRepository.findByVenueIdOrderByDisplayOrderAsc(venueId)).thenReturn(java.util.List.of(
                image(UUID.randomUUID(), false, 1), image(imageId, true, 2)));

        java.util.List<VenueImageResponse> response = service.setPrimaryImage(venueId, imageId,
                new CurrentActor(actorId, false));

        assertThat(response).extracting(VenueImageResponse::displayOrder).containsExactly(1, 2);
        assertThat(response).filteredOn(VenueImageResponse::isPrimary).singleElement()
                .extracting(VenueImageResponse::imageId).isEqualTo(imageId);
        var order = org.mockito.Mockito.inOrder(venueRepository, imageRepository);
        order.verify(venueRepository).lockRow(venueId);
        order.verify(imageRepository).clearPrimary(venueId);
        order.verify(imageRepository).setPrimary(venueId, imageId);
        order.verify(imageRepository).findByVenueIdOrderByDisplayOrderAsc(venueId);
    }

    @Test
    void setPrimaryReturnsVenue004WhenImageDoesNotBelongToVenue() {
        UUID imageId = UUID.randomUUID();
        lockedVenue(Venue.Status.ACTIVE);
        when(imageRepository.setPrimary(venueId, imageId)).thenReturn(0);

        assertError(() -> service.setPrimaryImage(venueId, imageId, new CurrentActor(actorId, false)),
                ErrorCode.VENUE_004);
        verify(imageRepository, org.mockito.Mockito.never()).findByVenueIdOrderByDisplayOrderAsc(any());
    }

    @Test
    void setPrimaryReturnsVenue001WhenVenueRowCannotBeLocked() {
        when(venueRepository.lockRow(venueId)).thenReturn(0);

        assertError(() -> service.setPrimaryImage(venueId, UUID.randomUUID(), new CurrentActor(actorId, false)),
                ErrorCode.VENUE_001);
        verifyNoInteractions(imageRepository);
    }

    @Test
    void setPrimaryOnInactiveVenueIsAdminOnly() {
        UUID imageId = UUID.randomUUID();
        lockedVenue(Venue.Status.INACTIVE);
        when(imageRepository.setPrimary(venueId, imageId)).thenReturn(1);
        when(imageRepository.findByVenueIdOrderByDisplayOrderAsc(venueId)).thenReturn(java.util.List.of(
                image(imageId, true, 1)));

        assertError(() -> service.setPrimaryImage(venueId, imageId, new CurrentActor(actorId, false)),
                ErrorCode.VENUE_002);
        verifyNoInteractions(imageRepository);

        assertThat(service.setPrimaryImage(venueId, imageId, new CurrentActor(actorId, true))).hasSize(1);
    }

    @Test
    void ownerDeletesNonPrimaryImageWithoutPromotionOrAuditAndGetsRemainingImages() {
        UUID imageId = UUID.randomUUID();
        UUID remainingId = UUID.randomUUID();
        lockedVenue(Venue.Status.ACTIVE, actorId);
        VenueImage target = image(imageId, false, 2);
        when(imageRepository.findByIdAndVenueId(imageId, venueId)).thenReturn(java.util.Optional.of(target));
        when(imageRepository.findByVenueIdOrderByDisplayOrderAsc(venueId)).thenReturn(java.util.List.of(
                image(remainingId, true, 1)));

        java.util.List<VenueImageResponse> response = service.deleteImage(venueId, imageId, new CurrentActor(actorId, false));

        assertThat(response).extracting(VenueImageResponse::imageId).containsExactly(remainingId);
        verify(imageRepository).delete(target);
        verify(imageRepository, org.mockito.Mockito.never()).promoteFirstAsPrimary(any());
        verifyNoInteractions(auditLog);
    }

    @Test
    void deletingPrimaryImagePromotesFirstRemainingImageAfterDelete() {
        UUID imageId = UUID.randomUUID();
        lockedVenue(Venue.Status.ACTIVE, actorId);
        VenueImage target = image(imageId, true, 1);
        when(imageRepository.findByIdAndVenueId(imageId, venueId)).thenReturn(java.util.Optional.of(target));
        when(imageRepository.findByVenueIdOrderByDisplayOrderAsc(venueId)).thenReturn(java.util.List.of());

        assertThat(service.deleteImage(venueId, imageId, new CurrentActor(actorId, false))).isEmpty();

        var order = org.mockito.Mockito.inOrder(venueRepository, imageRepository);
        order.verify(venueRepository).lockRow(venueId);
        order.verify(imageRepository).findByIdAndVenueId(imageId, venueId);
        order.verify(imageRepository).delete(target);
        order.verify(imageRepository).promoteFirstAsPrimary(venueId);
        order.verify(imageRepository).findByVenueIdOrderByDisplayOrderAsc(venueId);
    }

    @Test
    void deleteChecksOwnershipBeforeInactiveStateAndImageExistence() {
        UUID imageId = UUID.randomUUID();
        lockedVenue(Venue.Status.INACTIVE, UUID.randomUUID());

        assertError(() -> service.deleteImage(venueId, imageId, new CurrentActor(actorId, false)), ErrorCode.VENUE_013);
        verifyNoInteractions(imageRepository);
    }

    @Test
    void ownerCannotDeleteImageOfInactiveVenue() {
        lockedVenue(Venue.Status.INACTIVE, actorId);

        assertError(() -> service.deleteImage(venueId, UUID.randomUUID(), new CurrentActor(actorId, false)),
                ErrorCode.VENUE_002);
        verifyNoInteractions(imageRepository);
    }

    @Test
    void deleteReturnsVenue004WhenImageIsMissingOrBelongsToAnotherVenue() {
        UUID imageId = UUID.randomUUID();
        lockedVenue(Venue.Status.ACTIVE, actorId);
        when(imageRepository.findByIdAndVenueId(imageId, venueId)).thenReturn(java.util.Optional.empty());

        assertError(() -> service.deleteImage(venueId, imageId, new CurrentActor(actorId, false)), ErrorCode.VENUE_004);
        verify(imageRepository, org.mockito.Mockito.never()).delete(any(VenueImage.class));
    }

    @Test
    void deleteReturnsVenue001WhenVenueRowCannotBeLocked() {
        when(venueRepository.lockRow(venueId)).thenReturn(0);

        assertError(() -> service.deleteImage(venueId, UUID.randomUUID(), new CurrentActor(actorId, false)),
                ErrorCode.VENUE_001);
        verifyNoInteractions(imageRepository);
    }

    @Test
    void adminWhoIsNotCreatorDeletesOnInactiveVenueAndRegistersAuditAfterCommit() {
        UUID imageId = UUID.randomUUID();
        lockedVenue(Venue.Status.INACTIVE, UUID.randomUUID());
        VenueImage target = image(imageId, false, 1);
        when(imageRepository.findByIdAndVenueId(imageId, venueId)).thenReturn(java.util.Optional.of(target));
        when(imageRepository.findByVenueIdOrderByDisplayOrderAsc(venueId)).thenReturn(java.util.List.of());

        service.deleteImage(venueId, imageId, new CurrentActor(actorId, true));

        verify(imageRepository).delete(target);
        verify(auditLog).afterCommit(AdminAuditLog.ADMIN_DELETE_IMAGE, actorId, venueId, null);
    }

    @Test
    void adminWhoCreatedVenueDeletesWithoutAudit() {
        UUID imageId = UUID.randomUUID();
        lockedVenue(Venue.Status.ACTIVE, actorId);
        when(imageRepository.findByIdAndVenueId(imageId, venueId)).thenReturn(java.util.Optional.of(image(imageId, false, 1)));
        when(imageRepository.findByVenueIdOrderByDisplayOrderAsc(venueId)).thenReturn(java.util.List.of());

        service.deleteImage(venueId, imageId, new CurrentActor(actorId, true));

        verifyNoInteractions(auditLog);
    }

    private void lockedVenue(Venue.Status status) {
        lockedVenue(status, UUID.randomUUID());
    }

    private void lockedVenue(Venue.Status status, UUID creatorId) {
        when(venueRepository.lockRow(venueId)).thenReturn(1);
        when(venueRepository.findById(venueId)).thenReturn(java.util.Optional.of(
                Venue.builder().id(venueId).status(status).createdByUserId(creatorId).build()));
    }

    private VenueImage image(UUID imageId, boolean primary, int displayOrder) {
        VenueImage image = VenueImage.builder().id(imageId).venueId(venueId).publicId("public-" + imageId)
                .imageUrl("url-" + imageId).isPrimary(primary).displayOrder(displayOrder).uploadedBy(actorId).build();
        ReflectionTestUtils.setField(image, "createdAt", OffsetDateTime.parse("2026-11-18T00:00:00Z"));
        return image;
    }

    private static void assertError(Runnable operation, ErrorCode expected) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
