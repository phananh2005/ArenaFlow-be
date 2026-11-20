package com.phananh.arenaflow.application.support.storage;

import java.util.Optional;
import java.util.UUID;

/** Hợp đồng upload trực tiếp có chữ ký, độc lập với storage. */
public interface ImageUploadSigner {

    boolean isConfigured();

    UploadSignature signUpload(String scopePath, UUID uploaderId);

    Optional<SignedPublicId> parsePublicId(String publicId);

    boolean isUploadedBy(SignedPublicId signedPublicId, UUID userId);

    boolean verifyUploadResponse(String publicId, long version, String signature);

    String deliveryUrl(String publicId, long version, String format);

    String rootPrefix(String scopeRoot);
}
