package com.phananh.arenaflow.application.service.impl;

import com.phananh.arenaflow.application.dto.request.AddVenueImageRequest;
import com.phananh.arenaflow.application.dto.response.VenueImageResponse;
import com.phananh.arenaflow.application.dto.response.VenueImageUploadSignatureResponse;
import com.phananh.arenaflow.application.mapper.VenueMapper;
import com.phananh.arenaflow.application.service.VenueImageService;
import com.phananh.arenaflow.application.support.AdminAuditLog;
import com.phananh.arenaflow.application.support.DataIntegrityViolations;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Ảnh địa điểm: cấp chữ ký upload, thêm ảnh đã upload, đặt ảnh chính, xoá ảnh. */
@Service
public class VenueImageServiceImpl implements VenueImageService {

    static final int MAX_IMAGES_PER_VENUE = 5;
    private static final String PUBLIC_ID_UNIQUE_INDEX = "uq_venue_image_public_id";

    private final VenueGuard venueGuard;
    private final VenueImageRepository venueImageRepository;
    private final ImageUploadSigner imageUploadSigner;
    private final VenueMapper venueMapper;
    private final TransactionTemplate transactionTemplate;
    private final VenueImageCleanupProperties cleanupProperties;
    private final AdminAuditLog adminAuditLog;
    private final Clock clock;

    public VenueImageServiceImpl(VenueGuard venueGuard, VenueImageRepository venueImageRepository,
                          ImageUploadSigner imageUploadSigner, VenueMapper venueMapper,
                          TransactionTemplate transactionTemplate, VenueImageCleanupProperties cleanupProperties,
                          AdminAuditLog adminAuditLog, Clock clock) {
        this.venueGuard = venueGuard;
        this.venueImageRepository = venueImageRepository;
        this.imageUploadSigner = imageUploadSigner;
        this.venueMapper = venueMapper;
        this.transactionTemplate = transactionTemplate;
        this.cleanupProperties = cleanupProperties;
        this.adminAuditLog = adminAuditLog;
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

    @Override
    @Transactional
    public List<VenueImageResponse> setPrimaryImage(UUID venueId, UUID imageId, CurrentActor actor) {
        Venue venue = venueGuard.lockForImageWrite(venueId);
        venueGuard.requireWritable(venue, actor);
        // Hai câu lệnh: PostgreSQL kiểm unique index một-ảnh-chính theo từng dòng trong một UPDATE.
        venueImageRepository.clearPrimary(venueId);
        if (venueImageRepository.setPrimary(venueId, imageId) == 0) {
            throw new BusinessException(ErrorCode.VENUE_004);
        }
        return currentImages(venueId);
    }

    @Override
    @Transactional
    public List<VenueImageResponse> deleteImage(UUID venueId, UUID imageId, CurrentActor actor) {
        Venue venue = venueGuard.lockForImageWrite(venueId);
        venueGuard.requireOwnerOrAdmin(venue, actor);
        venueGuard.requireWritable(venue, actor);
        VenueImage image = venueImageRepository.findByIdAndVenueId(imageId, venueId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VENUE_004));
        // Asset Cloudinary để lại cho job dọn asset mồ côi: không gọi HTTP ra ngoài trong transaction.
        venueImageRepository.delete(image);
        if (Boolean.TRUE.equals(image.getIsPrimary())) {
            venueImageRepository.promoteFirstAsPrimary(venueId);
        }
        if (actor.admin() && !actor.userId().equals(venue.getCreatedByUserId())) {
            adminAuditLog.afterCommit(AdminAuditLog.ADMIN_DELETE_IMAGE, actor.userId(), venueId, null);
        }
        return currentImages(venueId);
    }

    private List<VenueImageResponse> currentImages(UUID venueId) {
        return venueImageRepository.findByVenueIdOrderByDisplayOrderAsc(venueId).stream()
                .map(venueMapper::toImage)
                .toList();
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
            // Flush ngay để public ID Cloudinary trùng được ánh xạ thành VENUE_006 ngay trong transaction này.
            return venueMapper.toImage(venueImageRepository.saveAndFlush(image));
        } catch (DataIntegrityViolationException exception) {
            if (DataIntegrityViolations.violates(exception, PUBLIC_ID_UNIQUE_INDEX)) {
                throw new BusinessException(ErrorCode.VENUE_006);
            }
            throw exception;
        }
    }
}
