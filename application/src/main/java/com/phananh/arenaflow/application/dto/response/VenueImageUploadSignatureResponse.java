package com.phananh.arenaflow.application.dto.response;

import java.time.LocalDateTime;

/**
 * Chữ ký để FE upload ảnh địa điểm thẳng lên Cloudinary; các field gửi lại Cloudinary giữ nguyên giá trị.
 *
 * @param uploadUrl      URL FE gửi file tới
 * @param cloudName      tên cloud Cloudinary
 * @param apiKey         gửi dưới tên {@code api_key}; không phải bí mật
 * @param publicId       gửi dưới tên {@code public_id}; FE không sửa
 * @param timestamp      thời điểm ký, Unix giây (tên tham số của Cloudinary)
 * @param allowedFormats gửi dưới tên {@code allowed_formats}
 * @param overwrite      luôn false
 * @param signature      chữ ký backend cấp
 * @param expiresAt      hạn chót upload (1 giờ sau {@code timestamp}), giờ Việt Nam không kèm múi giờ
 */
public record VenueImageUploadSignatureResponse(String uploadUrl, String cloudName, String apiKey, String publicId,
                                                long timestamp, String allowedFormats, boolean overwrite,
                                                String signature, LocalDateTime expiresAt) {
}
