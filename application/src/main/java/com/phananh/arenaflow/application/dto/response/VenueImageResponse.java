package com.phananh.arenaflow.application.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Một ảnh của địa điểm (không trả {@code publicId} của storage).
 *
 * @param imageId      id ảnh
 * @param venueId      id địa điểm
 * @param imageUrl     URL ảnh do backend tạo, hiển thị trực tiếp
 * @param isPrimary    true với đúng một ảnh khi địa điểm có ảnh
 * @param displayOrder thứ tự hiển thị tăng dần, có thể không liên tục
 * @param uploadedById người đã thêm ảnh
 * @param createdAt    thời điểm thêm ảnh
 */
public record VenueImageResponse(UUID imageId, UUID venueId, String imageUrl, boolean isPrimary, int displayOrder,
                                 UUID uploadedById, LocalDateTime createdAt) {
}
