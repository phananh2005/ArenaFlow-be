package com.phananh.arenaflow.application.service.impl;

import com.phananh.arenaflow.application.dto.request.AddVenueImageRequest;
import com.phananh.arenaflow.application.dto.response.VenueImageResponse;
import com.phananh.arenaflow.application.dto.response.VenueImageUploadSignatureResponse;
import com.phananh.arenaflow.application.mapper.VenueMapper;
import com.phananh.arenaflow.application.service.VenueImageService;
import com.phananh.arenaflow.application.support.VenueGuard;
import com.phananh.arenaflow.application.support.security.CurrentActor;
import com.phananh.arenaflow.application.support.storage.ImageUploadSigner;
import com.phananh.arenaflow.application.support.venue.VenueImageCleanupProperties;
import com.phananh.arenaflow.application.support.storage.SignedPublicId;
import com.phananh.arenaflow.application.support.storage.UploadSignature;
import com.phananh.arenaflow.domain.entity.venue.VenueImage;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.entity.venue.Venue;
import com.phananh.arenaflow.domain.repository.VenueImageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class VenueImageServiceImpl implements VenueImageService {

    static final int MAX_IMAGES_PER_VENUE = 5;

    private final VenueGuard venueGuard;
    private final VenueImageRepository venueImageRepository;
    private final ImageUploadSigner imageUploadSigner;
    private final VenueMapper venueMapper;
    private final TransactionTemplate transactionTemplate;
    private final VenueImageCleanupProperties cleanupProperties;
    private final Clock clock;

    @Autowired
    public VenueImageServiceImpl(VenueGuard venueGuard, VenueImageRepository venueImageRepository,
                                 ImageUploadSigner imageUploadSigner, VenueMapper venueMapper,
                                 TransactionTemplate transactionTemplate, VenueImageCleanupProperties cleanupProperties) {
        this(venueGuard, venueImageRepository, imageUploadSigner, venueMapper, transactionTemplate, cleanupProperties,
                Clock.systemUTC());
    }

    public VenueImageServiceImpl(VenueGuard venueGuard, VenueImageRepository venueImageRepository,
                          ImageUploadSigner imageUploadSigner, VenueMapper venueMapper,
                          TransactionTemplate transactionTemplate, VenueImageCleanupProperties cleanupProperties, Clock clock) {
        this.venueGuard = venueGuard;
        this.venueImageRepository = venueImageRepository;
        this.imageUploadSigner = imageUploadSigner;
        this.venueMapper = venueMapper;
        this.transactionTemplate = transactionTemplate;
        this.cleanupProperties = cleanupProperties;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public VenueImageUploadSignatureResponse issueUploadSignature(UUID venueId, CurrentActor actor) {
        if (!imageUploadSigner.isConfigured()) {
            throw new BusinessException(ErrorCode.VENUE_012);
        }
        Venue venue = venueGuard.findVenue(venueId);
        venueGuard.requireWritable(venue, actor);
        if (venueImageRepository.countByVenueId(venueId) >= MAX_IMAGES_PER_VENUE) {
            throw new BusinessException(ErrorCode.VENUE_003);
        }
        UploadSignature signature = imageUploadSigner.signUpload("venues/" + venueId, actor.userId());
        return new VenueImageUploadSignatureResponse(signature.uploadUrl(), signature.cloudName(), signature.apiKey(),
                signature.publicId(), signature.timestamp(), signature.allowedFormats(), signature.overwrite(),
                signature.signature(), signature.expiresAt());
    }

    @Override
    public VenueImageResponse addImage(UUID venueId, CurrentActor actor, AddVenueImageRequest request) {
        if (!imageUploadSigner.isConfigured()) {
            throw new BusinessException(ErrorCode.VENUE_012);
        }
        imageUploadSigner.parsePublicId(request.publicId())
                .filter(value -> value.scopePath().equals("venues/" + venueId))
                .filter(this::isWithinConfirmationWindow)
                .filter(value -> imageUploadSigner.isUploadedBy(value, actor.userId()))
                .filter(value -> imageUploadSigner.verifyUploadResponse(request.publicId(), request.version(), request.signature()))
                .orElseThrow(() -> new BusinessException(ErrorCode.VENUE_005));
        return transactionTemplate.execute(status -> addVerifiedImage(venueId, actor, request));
    }

    private boolean isWithinConfirmationWindow(SignedPublicId signedPublicId) {
        Instant now = clock.instant();
        return !signedPublicId.signedAt().isAfter(now.plus(cleanupProperties.clockSkew()))
                && !signedPublicId.signedAt().plus(cleanupProperties.confirmWindow()).isBefore(now);
    }

    private VenueImageResponse addVerifiedImage(UUID venueId, CurrentActor actor, AddVenueImageRequest request) {
        Venue venue = venueGuard.lockForImageWrite(venueId);
        venueGuard.requireWritable(venue, actor);
        var stats = venueImageRepository.findStatsByVenueId(venueId);
        if (stats.getImageCount() >= MAX_IMAGES_PER_VENUE) {
            throw new BusinessException(ErrorCode.VENUE_003);
        }
        boolean primary = stats.getImageCount() == 0 || Boolean.TRUE.equals(request.isPrimary());
        if (primary && stats.getImageCount() > 0) {
            venueImageRepository.clearPrimary(venueId);
        }
        VenueImage image = VenueImage.builder()
                .venueId(venueId)
                .publicId(request.publicId())
                .imageUrl(imageUploadSigner.deliveryUrl(request.publicId(), request.version(), request.format()))
                .isPrimary(primary)
                .displayOrder(stats.getMaxDisplayOrder() + 1)
                .uploadedBy(actor.userId())
                .build();
        try {
            // Flush immediately so a duplicate Cloudinary public ID maps to VENUE_006 inside this transaction.
            return venueMapper.toImage(venueImageRepository.saveAndFlush(image));
        } catch (DataIntegrityViolationException duplicatePublicId) {
            throw new BusinessException(ErrorCode.VENUE_006);
        }
    }
}
