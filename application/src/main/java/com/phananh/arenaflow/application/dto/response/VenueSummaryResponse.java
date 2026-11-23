package com.phananh.arenaflow.application.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Một dòng trong danh sách địa điểm.
 *
 * @param venueId         id địa điểm
 * @param venueName       tên địa điểm
 * @param address         địa chỉ
 * @param latitude        vĩ độ
 * @param longitude       kinh độ
 * @param venueType       loại sân; null khi chưa khai báo
 * @param status          {@code ACTIVE} hoặc {@code INACTIVE}
 * @param primaryImageUrl URL ảnh chính; null khi chưa có ảnh
 * @param distanceKm      khoảng cách (km) tới toạ độ tìm kiếm, làm tròn 2 chữ số thập phân; null khi không gửi toạ độ
 * @param lastSelectedAt  lần gần nhất địa điểm được chọn; null khi chưa từng
 */
public record VenueSummaryResponse(UUID venueId, String venueName, String address, BigDecimal latitude,
                                   BigDecimal longitude, String venueType, String status, String primaryImageUrl,
                                   BigDecimal distanceKm, LocalDateTime lastSelectedAt) {
}
