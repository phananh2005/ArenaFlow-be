package com.phananh.arenaflow.application.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Report địa điểm cho Admin: tên người report và người xử lý luôn là tên thật, kèm cờ đã xoá tài khoản.
 *
 * @param reportId           id report
 * @param venueId            id địa điểm
 * @param venueName          tên địa điểm
 * @param venueStatus        trạng thái hiện tại của địa điểm
 * @param reporterId         id người report
 * @param reporterName       tên thật người report
 * @param isReporterDeleted  true khi người report đã xoá tài khoản
 * @param reason             lý do report
 * @param detail             mô tả thêm; có thể null
 * @param status             {@code PENDING}, {@code VERIFIED} hoặc {@code REJECTED}
 * @param handledById        Admin xử lý; null khi {@code PENDING}
 * @param handledByName      tên thật Admin xử lý; null khi {@code PENDING}
 * @param isHandledByDeleted true khi Admin xử lý đã xoá tài khoản; false khi {@code handledByName} null
 * @param handledAt          thời điểm xử lý; null khi {@code PENDING}
 * @param createdAt          thời điểm gửi report
 */
public record AdminVenueReportResponse(UUID reportId, UUID venueId, String venueName, String venueStatus,
                                       UUID reporterId, String reporterName, boolean isReporterDeleted,
                                       String reason, String detail, String status,
                                       UUID handledById, String handledByName, boolean isHandledByDeleted,
                                       LocalDateTime handledAt, LocalDateTime createdAt) {
}
