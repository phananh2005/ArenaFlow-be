package com.phananh.arenaflow.application.service.impl;

import com.phananh.arenaflow.application.dto.request.AdminSearchVenueRequest;
import com.phananh.arenaflow.application.dto.request.ResolveVenueReportRequest;
import com.phananh.arenaflow.application.dto.response.AdminVenueReportResponse;
import com.phananh.arenaflow.application.dto.response.AdminVenueResponse;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.dto.response.VenueSummaryResponse;
import com.phananh.arenaflow.application.mapper.VenueMapper;
import com.phananh.arenaflow.application.service.AdminVenueService;
import com.phananh.arenaflow.application.service.VenueReportStatusFilter;
import com.phananh.arenaflow.application.service.VenueStatusFilter;
import com.phananh.arenaflow.application.support.AdminAuditLog;
import com.phananh.arenaflow.application.support.ConcurrencyConflictRetrier;
import com.phananh.arenaflow.application.support.LikePatterns;
import com.phananh.arenaflow.application.support.PageRequestFactory;
import com.phananh.arenaflow.application.support.VenueGuard;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.entity.venue.Venue;
import com.phananh.arenaflow.domain.entity.venue.VenueReport;
import com.phananh.arenaflow.domain.projection.VenueSummaryView;
import com.phananh.arenaflow.domain.repository.VenueImageRepository;
import com.phananh.arenaflow.domain.repository.VenueReportRepository;
import com.phananh.arenaflow.domain.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Nghiệp vụ Admin trên địa điểm; quyền Admin đã được chặn ở tầng web theo đường dẫn {@code /api/v1/admin/**}. */
@Service
@RequiredArgsConstructor
public class AdminVenueServiceImpl implements AdminVenueService {

    private final VenueGuard venueGuard;
    private final VenueRepository venueRepository;
    private final VenueReportRepository venueReportRepository;
    private final VenueImageRepository venueImageRepository;
    private final ConcurrencyConflictRetrier concurrencyConflictRetrier;
    private final VenueMapper venueMapper;
    private final PageRequestFactory pageRequestFactory;
    private final AdminAuditLog adminAuditLog;

    /**
     * Q1 trang + Q2 count. {@code ALL} dùng truy vấn không lọc trạng thái, trạng thái cụ thể dùng truy vấn có index
     * theo trạng thái; cả hai sắp theo tên rồi id.
     */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<VenueSummaryResponse> listVenues(AdminSearchVenueRequest request) {
        var pageable = pageRequestFactory.create(request.page(), request.pageSize());
        String qPattern = LikePatterns.contains(request.q());
        Page<VenueSummaryView> venues = request.status() == VenueStatusFilter.ALL
                ? venueRepository.searchAll(qPattern, pageable)
                : venueRepository.searchByStatus(request.status().toQueryValue(), qPattern, pageable);
        return PageResponse.from(venues, venueMapper::toSummary, request.page(), request.pageSize());
    }

    /** Q1 venue, Q2 cập nhật CAS, Q3 bulk xác minh các report CLOSED đang chờ, Q4–Q5 chi tiết cho admin. */
    @Override
    public AdminVenueResponse markInactive(UUID venueId, UUID adminId) {
        return concurrencyConflictRetrier.execute(ErrorCode.VENUE_011, () -> {
            Venue venue = venueGuard.findVenue(venueId);
            if (venue.getStatus() == Venue.Status.INACTIVE) {
                throw new BusinessException(ErrorCode.VENUE_007);
            }
            OffsetDateTime now = OffsetDateTime.now();
            venue.setStatus(Venue.Status.INACTIVE);
            venue.setAdminVerifiedInactiveAt(now);
            venueRepository.save(venue);
            // Hibernate flush cập nhật venue có version trước câu native bulk update này.
            venueReportRepository.verifyPendingClosureReports(venueId, adminId, now);
            adminAuditLog.afterCommit(AdminAuditLog.MARK_INACTIVE, adminId, venueId, null);
            return adminDetail(venueId);
        });
    }

    /** Q1 venue, Q2 cập nhật CAS, Q3–Q4 chi tiết cho admin; không đụng tới report. */
    @Override
    public AdminVenueResponse reactivate(UUID venueId, UUID adminId) {
        return concurrencyConflictRetrier.execute(ErrorCode.VENUE_011, () -> {
            Venue venue = venueGuard.findVenue(venueId);
            if (venue.getStatus() == Venue.Status.ACTIVE) {
                throw new BusinessException(ErrorCode.VENUE_008);
            }
            venue.setStatus(Venue.Status.ACTIVE);
            venue.setAdminVerifiedInactiveAt(null);
            venue.setStaleAlertedAt(null);
            venue.setClosureAlertedAt(null);
            venueRepository.save(venue);
            adminAuditLog.afterCommit(AdminAuditLog.REACTIVATE, adminId, venueId, null);
            return adminDetail(venueId);
        });
    }

    /** Q1 trang + Q2 count. */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminVenueReportResponse> listReports(VenueReportStatusFilter status, UUID venueId,
                                                              int page, int pageSize) {
        return PageResponse.from(venueReportRepository.findAdminReports(status.toQueryValue(), venueId,
                        pageRequestFactory.create(page, pageSize)),
                venueMapper::toAdminReport, page, pageSize);
    }

    /** Q1 report, Q2 xử lý có điều kiện, (Q3 đặt lại cảnh báo đóng cửa), Q4 view cho admin. */
    @Override
    public AdminVenueReportResponse resolveReport(UUID reportId, UUID adminId, ResolveVenueReportRequest request) {
        return concurrencyConflictRetrier.execute(ErrorCode.VENUE_011, () -> {
            VenueReport report = venueReportRepository.findById(reportId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.VENUE_009));
            if (report.getStatus() != VenueReport.Status.PENDING
                    || venueReportRepository.resolveIfPending(reportId, request.status(), adminId, OffsetDateTime.now()) == 0) {
                throw new BusinessException(ErrorCode.VENUE_010);
            }
            if (report.getReason() == VenueReport.Reason.CLOSED) {
                venueRepository.reopenClosureAlertIfNoPending(report.getVenueId());
            }
            adminAuditLog.afterCommit(AdminAuditLog.RESOLVE_REPORT, adminId, report.getVenueId(), reportId);
            return venueReportRepository.findAdminViewById(reportId)
                    .map(venueMapper::toAdminReport)
                    .orElseThrow(() -> new BusinessException(ErrorCode.VENUE_009));
        });
    }

    private AdminVenueResponse adminDetail(UUID venueId) {
        return venueRepository.findDetailById(venueId)
                .map(view -> venueMapper.toAdminResponse(view, venueImageRepository.findByVenueIdOrderByDisplayOrderAsc(venueId)))
                .orElseThrow(() -> new BusinessException(ErrorCode.VENUE_001));
    }
}
