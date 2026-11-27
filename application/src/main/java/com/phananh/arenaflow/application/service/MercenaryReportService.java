package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.CreateReportRequest;
import com.phananh.arenaflow.application.dto.response.MercenaryReportResponse;
import com.phananh.arenaflow.application.dto.response.PageResponse;

import java.util.UUID;

/** Report giữa đội tuyển và mercenary / ứng viên. */
public interface MercenaryReportService {

    MercenaryReportResponse createReport(UUID applicationId, UUID callerId, CreateReportRequest request);

    PageResponse<MercenaryReportResponse> listTeamReports(UUID teamId, UUID callerId, MercenaryReportDirection direction,
                                                          int page, int pageSize);

    PageResponse<MercenaryReportResponse> listMyReports(UUID callerId, MercenaryReportDirection direction, int page,
                                                        int pageSize);
}
