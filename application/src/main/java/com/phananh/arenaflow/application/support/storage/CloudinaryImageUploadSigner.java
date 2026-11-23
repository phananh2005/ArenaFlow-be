package com.phananh.arenaflow.application.support.storage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Ký và kiểm tra upload ảnh trực tiếp lên Cloudinary mà không gọi Cloudinary. */
@Component
public class CloudinaryImageUploadSigner implements ImageUploadSigner {

    private static final String ALLOWED_FORMATS = "jpg,jpeg,png,webp";
    private static final long SIGNATURE_TTL_SECONDS = 3_600;
    private static final int NONCE_BYTES = 16;
    private static final int TAG_LENGTH = 22;
    private static final String TAG_DOMAIN = "arenaflow-upload-tag";
    private static final Pattern SIGNED_PUBLIC_ID =
            Pattern.compile("^(.+)/([0-9]{10})-([A-Za-z0-9_-]{22})-([A-Za-z0-9_-]{22})$");

    private final CloudinaryProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom;

    /** Constructor cho Spring: nonce sinh bằng {@link SecureRandom} mặc định. */
    @Autowired
    public CloudinaryImageUploadSigner(CloudinaryProperties properties, Clock clock) {
        this(properties, clock, new SecureRandom());
    }

    CloudinaryImageUploadSigner(CloudinaryProperties properties, Clock clock, SecureRandom secureRandom) {
        this.properties = properties;
        this.clock = clock;
        this.secureRandom = secureRandom;
    }

    @Override
    public boolean isConfigured() {
        return properties.isConfigured();
    }

    /**
     * Ký upload một ảnh vào {@code scopePath}: {@code public_id} gắn thời điểm ký (epoch giây), nonce và tag ràng buộc
     * người upload; chữ ký hết hạn sau 1 giờ. {@code timestamp} giữ epoch giây (Cloudinary yêu cầu),
     * {@code expiresAt} đổi sang giờ địa phương theo zone của bean {@code Clock}.
     *
     * @throws IllegalStateException khi chưa cấu hình Cloudinary
     */
    @Override
    public UploadSignature signUpload(String scopePath, UUID uploaderId) {
        requireConfigured();
        Instant now = clock.instant();
        long timestamp = now.getEpochSecond();
        String nonce = randomUrlToken(NONCE_BYTES);
        String tag = tag(scopePath, timestamp, nonce, uploaderId);
        String publicId = rootPrefix("") + scopePath + "/" + timestamp + "-" + nonce + "-" + tag;
        String parameters = "allowed_formats=" + ALLOWED_FORMATS + "&overwrite=false&public_id=" + publicId
                + "&timestamp=" + timestamp;
        return new UploadSignature("https://api.cloudinary.com/v1_1/" + properties.cloudName() + "/image/upload",
                properties.cloudName(), properties.apiKey(), publicId, timestamp, ALLOWED_FORMATS, false,
                sha1Hex(parameters + properties.apiSecret()),
                LocalDateTime.ofInstant(now.plusSeconds(SIGNATURE_TTL_SECONDS), clock.getZone()));
    }

    @Override
    public Optional<SignedPublicId> parsePublicId(String publicId) {
        if (!isConfigured() || publicId == null) {
            return Optional.empty();
        }
        String prefix = rootPrefix("");
        if (!publicId.startsWith(prefix)) {
            return Optional.empty();
        }
        String remaining = publicId.substring(prefix.length());
        Matcher matcher = SIGNED_PUBLIC_ID.matcher(remaining);
        if (!matcher.matches() || matcher.group(1).contains("..")) {
            return Optional.empty();
        }
        try {
            return Optional.of(new SignedPublicId(matcher.group(1), Instant.ofEpochSecond(Long.parseLong(matcher.group(2))),
                    matcher.group(3), matcher.group(4)));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    @Override
    public boolean isUploadedBy(SignedPublicId signedPublicId, UUID userId) {
        if (!isConfigured() || signedPublicId == null || userId == null) {
            return false;
        }
        String expected = tag(signedPublicId.scopePath(), signedPublicId.signedAt().getEpochSecond(),
                signedPublicId.nonce(), userId);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                signedPublicId.tag().getBytes(StandardCharsets.US_ASCII));
    }

    @Override
    public boolean verifyUploadResponse(String publicId, long version, String signature) {
        if (!isConfigured() || publicId == null || signature == null || version <= 0) {
            return false;
        }
        String expected = sha1Hex("public_id=" + publicId + "&version=" + version + properties.apiSecret());
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), signature.getBytes(StandardCharsets.US_ASCII));
    }

    @Override
    public String deliveryUrl(String publicId, long version, String format) {
        requireConfigured();
        return "https://res.cloudinary.com/" + properties.cloudName() + "/image/upload/v" + version + "/"
                + publicId + "." + format;
    }

    @Override
    public String rootPrefix(String scopeRoot) {
        requireConfigured();
        String normalizedRoot = scopeRoot == null || scopeRoot.isBlank() ? "" : scopeRoot.trim() + "/";
        return properties.folder().trim() + "/" + normalizedRoot;
    }

    private String tag(String scopePath, long signedAt, String nonce, UUID uploaderId) {
        byte[] tagKey = hmac(properties.apiSecret().getBytes(StandardCharsets.UTF_8), TAG_DOMAIN);
        byte[] tag = hmac(tagKey, scopePath + "|" + signedAt + "|" + nonce + "|" + uploaderId);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tag).substring(0, TAG_LENGTH);
    }

    private static byte[] hmac(byte[] key, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not calculate upload tag", exception);
        }
    }

    private String randomUrlToken(int bytes) {
        byte[] value = new byte[bytes];
        secureRandom.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException("Cloudinary is not configured");
        }
    }

    private static String sha1Hex(String input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1")
                    .digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not calculate Cloudinary signature", exception);
        }
    }
}
