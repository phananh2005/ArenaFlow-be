package com.phananh.arenaflow.application.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Chi tiết địa điểm cho Admin: field như {@link VenueResponse}, khác ở tên người tạo luôn là tên thật và có thêm cờ
 * đã xoá tài khoản.
 *
 * @param venueId                 id địa điểm
 * @param venueName               tên địa điểm
 * @param address                 địa chỉ
 * @param latitude                vĩ độ
 * @param longitude               kinh độ
 * @param ward                    phường/xã kèm tỉnh; null khi địa điểm chưa được bổ sung phường
 * @param venueType               loại sân; null khi chưa khai báo
 * @param phone                   số điện thoại liên hệ; có thể null
 * @param website                 website; có thể null
 * @param status                  {@code ACTIVE} hoặc {@code INACTIVE}
 * @param createdByType           {@code ADMIN} hoặc {@code USER}
 * @param createdById             id người tạo; null khi địa điểm không còn gắn người tạo
 * @param createdByName           tên thật người tạo, kể cả khi đã xoá tài khoản; null khi {@code createdById} null
 * @param isCreatedByDeleted      true khi người tạo đã xoá tài khoản; false khi {@code createdById} null
 * @param lastSelectedAt          lần gần nhất địa điểm được chọn cho kèo hoặc trận ngoài hệ thống; null khi chưa từng
 * @param adminVerifiedInactiveAt thời điểm Admin đánh dấu ngừng hoạt động; null khi {@code ACTIVE}
 * @param createdAt               thời điểm tạo
 * @param updatedAt               thời điểm sửa gần nhất
 * @param images                  ảnh theo {@code displayOrder} tăng dần
 */
public record AdminVenueResponse(UUID venueId, String venueName, String address, BigDecimal latitude, BigDecimal longitude,
                                 WardResponse ward, String venueType, String phone, String website, String status,
                                 String createdByType, UUID createdById, String createdByName, boolean isCreatedByDeleted,
                                 LocalDateTime lastSelectedAt, LocalDateTime adminVerifiedInactiveAt,
                                 LocalDateTime createdAt, LocalDateTime updatedAt, List<VenueImageResponse> images) {
}
