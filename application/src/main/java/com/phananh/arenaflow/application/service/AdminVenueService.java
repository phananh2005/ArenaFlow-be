package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.ResolveVenueReportRequest;
import com.phananh.arenaflow.application.dto.response.AdminVenueReportResponse;
import com.phananh.arenaflow.application.dto.response.AdminVenueResponse;
import com.phananh.arenaflow.application.dto.response.PageResponse;

import java.util.UUID;

public interface AdminVenueService {

    AdminVenueResponse markInactive(UUID venueId, UUID adminId);

    AdminVenueResponse reactivate(UUID venueId, UUID adminId);

    PageResponse<AdminVenueReportResponse> listReports(VenueReportStatusFilter status, UUID venueId, int page, int pageSize);

    AdminVenueReportResponse resolveReport(UUID reportId, UUID adminId, ResolveVenueReportRequest request);
}
