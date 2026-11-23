package com.phananh.arenaflow.application.support.storage;

import java.time.LocalDateTime;

/**
 * Kết quả ký upload của {@link ImageUploadSigner}, chưa gắn với địa điểm cụ thể.
 *
 * @param uploadUrl      URL endpoint upload của storage
 * @param cloudName      tên cloud
 * @param apiKey         API key công khai
 * @param publicId       public id đã ký, chứa thời điểm ký, nonce và tag
 * @param timestamp      thời điểm ký, Unix giây
 * @param allowedFormats định dạng ảnh cho phép, cách nhau bằng dấu phẩy
 * @param overwrite      luôn false: không ghi đè asset đã có
 * @param signature      chữ ký SHA-1 của các tham số
 * @param expiresAt      hạn chót upload, giờ địa phương theo zone của bean {@code Clock}
 */
public record UploadSignature(String uploadUrl, String cloudName, String apiKey, String publicId, long timestamp,
                              String allowedFormats, boolean overwrite, String signature, LocalDateTime expiresAt) {
}
