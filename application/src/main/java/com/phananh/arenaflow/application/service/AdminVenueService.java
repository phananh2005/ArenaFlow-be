package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.AdminSearchVenueRequest;
import com.phananh.arenaflow.application.dto.request.ResolveVenueReportRequest;
import com.phananh.arenaflow.application.dto.response.AdminVenueReportResponse;
import com.phananh.arenaflow.application.dto.response.AdminVenueResponse;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.dto.response.VenueSummaryResponse;

import java.util.UUID;

/** Thao tác chỉ dành cho Admin trên địa điểm: danh sách toàn bộ địa điểm, ngừng/kích hoạt lại, xử lý report. */
public interface AdminVenueService {

    /**
     * Danh sách địa điểm mọi trạng thái (mặc định), không lọc theo vị trí, sắp theo tên rồi id.
     *
     * @param request từ khoá, trạng thái và phân trang đã có giá trị mặc định
     * @return một trang địa điểm, {@code distanceKm} luôn {@code null}
     */
    PageResponse<VenueSummaryResponse> listVenues(AdminSearchVenueRequest request);

    AdminVenueResponse markInactive(UUID venueId, UUID adminId);

    AdminVenueResponse reactivate(UUID venueId, UUID adminId);

    PageResponse<AdminVenueReportResponse> listReports(VenueReportStatusFilter status, UUID venueId, int page, int pageSize);

    AdminVenueReportResponse resolveReport(UUID reportId, UUID adminId, ResolveVenueReportRequest request);
}
