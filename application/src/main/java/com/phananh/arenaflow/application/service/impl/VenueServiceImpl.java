package com.phananh.arenaflow.application.service.impl;

import com.phananh.arenaflow.application.dto.request.CreateVenueRequest;
import com.phananh.arenaflow.application.dto.request.ReportVenueRequest;
import com.phananh.arenaflow.application.dto.request.SearchVenueRequest;
import com.phananh.arenaflow.application.dto.request.UpdateVenueRequest;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.dto.response.VenueReportResponse;
import com.phananh.arenaflow.application.dto.response.VenueResponse;
import com.phananh.arenaflow.application.dto.response.VenueSummaryResponse;
import com.phananh.arenaflow.application.dto.response.WardResponse;
import com.phananh.arenaflow.application.event.venue.VenueAdminAlertEvent;
import com.phananh.arenaflow.application.event.venue.VenueAlertType;
import com.phananh.arenaflow.application.mapper.VenueMapper;
import com.phananh.arenaflow.application.service.VenueService;
import com.phananh.arenaflow.application.service.VenueStatusFilter;
import com.phananh.arenaflow.application.support.AdminAuditLog;
import com.phananh.arenaflow.application.support.PageRequestFactory;
import com.phananh.arenaflow.application.support.ConcurrencyConflictRetrier;
import com.phananh.arenaflow.application.support.DataIntegrityViolations;
import com.phananh.arenaflow.application.support.LikePatterns;
import com.phananh.arenaflow.application.support.VenueGuard;
import com.phananh.arenaflow.application.support.security.CurrentActor;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.entity.user.User;
import com.phananh.arenaflow.domain.entity.venue.Venue;
import com.phananh.arenaflow.domain.entity.venue.VenueReport;
import com.phananh.arenaflow.domain.projection.VenueSummaryView;
import com.phananh.arenaflow.domain.projection.WardView;
import com.phananh.arenaflow.domain.repository.AdministrativeUnitRepository;
import com.phananh.arenaflow.domain.repository.UserRepository;
import com.phananh.arenaflow.domain.repository.VenueImageRepository;
import com.phananh.arenaflow.domain.repository.VenueReportRepository;
import com.phananh.arenaflow.domain.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Nghiệp vụ địa điểm sân cho người dùng đã đăng nhập: thêm, xem, tìm, sửa và report địa điểm.
 * Điện thoại/website chỉ Admin được ghi; danh sách chỉ trả địa điểm đang hoạt động.
 */
@Service
@RequiredArgsConstructor
public class VenueServiceImpl implements VenueService {

    private static final String PENDING_REPORT_UNIQUE_INDEX = "uq_venue_report_pending";

    private final UserRepository userRepository;
    private final AdministrativeUnitRepository administrativeUnitRepository;
    private final VenueRepository venueRepository;
    private final VenueImageRepository venueImageRepository;
    private final VenueReportRepository venueReportRepository;
    private final VenueMapper venueMapper;
    private final PageRequestFactory pageRequestFactory;
    private final VenueGuard venueGuard;
    private final ConcurrencyConflictRetrier concurrencyConflictRetrier;
    private final ApplicationEventPublisher eventPublisher;
    private final AdminAuditLog adminAuditLog;

    /**
     * Q1 caller, Q2 phường/tỉnh, Q3 insert; trả kết quả từ dữ liệu đã đọc, không cần truy vấn thứ tư.
     * Caller không phải Admin gửi điện thoại/website (kể cả chuỗi rỗng) → {@code VENUE_017} trước khi tra phường;
     * vai trò lấy từ bản ghi user vừa đọc, cùng nguồn với {@code createdByType}.
     */
    @Override
    @Transactional
    public VenueResponse createVenue(UUID callerId, CreateVenueRequest request) {
        User creator = userRepository.findByIdAndDeletedAtIsNull(callerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_001));
        requireAdminForContactFields(creator.getRole() == User.Role.ADMIN, request.phone(), request.website());
        WardView ward = administrativeUnitRepository.findWardWithProvince(request.wardId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_002));
        Venue venue = venueRepository.save(Venue.builder()
                .name(request.venueName().trim())
                .address(request.address().trim())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .wardId(request.wardId())
                .venueType(emptyToNull(request.venueType()) == null ? null : Venue.VenueType.valueOf(request.venueType()))
                .phone(emptyToNull(request.phone()))
                .website(emptyToNull(request.website()))
                .createdByType(creator.getRole() == User.Role.ADMIN ? Venue.CreatedByType.ADMIN : Venue.CreatedByType.USER)
                .createdByUserId(callerId)
                .build());
        return new VenueResponse(venue.getId(), venue.getName(), venue.getAddress(), venue.getLatitude(), venue.getLongitude(),
                new WardResponse(ward.getWardId(), ward.getWardName(), ward.getProvinceId(), ward.getProvinceName()),
                venue.getVenueType() == null ? null : venue.getVenueType().name(), venue.getPhone(), venue.getWebsite(),
                venue.getStatus().name(), venue.getCreatedByType().name(), callerId, creator.getDisplayName(),
                venue.getLastSelectedAt(), venue.getAdminVerifiedInactiveAt(), venue.getCreatedAt(), venue.getUpdatedAt(), List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public VenueResponse getVenue(UUID venueId) {
        return venueRepository.findDetailById(venueId)
                .map(view -> venueMapper.toResponse(view, venueImageRepository.findByVenueIdOrderByDisplayOrderAsc(venueId)))
                .orElseThrow(() -> new BusinessException(ErrorCode.VENUE_001));
    }

    /**
     * Q1 trang + Q2 count; chỉ địa điểm đang hoạt động, chọn native query có index phù hợp (có hoặc không toạ độ).
     * Lọc trạng thái khác {@code ACTIVE} → {@code VENUE_018} trước mọi truy vấn, kể cả khi caller là Admin
     * (Admin xem toàn bộ địa điểm qua danh sách riêng của Admin).
     */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<VenueSummaryResponse> listVenues(SearchVenueRequest request) {
        if (request.status() != VenueStatusFilter.ACTIVE) {
            throw new BusinessException(ErrorCode.VENUE_018);
        }
        var pageable = pageRequestFactory.create(request.page(), request.pageSize());
        String qPattern = LikePatterns.contains(request.q());
        Page<VenueSummaryView> venues;
        if (request.lat() == null) {
            venues = venueRepository.searchByStatus(VenueStatusFilter.ACTIVE.toQueryValue(), qPattern, pageable);
        } else {
            double lat = request.lat().doubleValue();
            double lng = request.lng().doubleValue();
            double radiusKm = request.radiusKm().doubleValue();
            double latitudeDelta = radiusKm / 111.32d;
            double longitudeDelta = radiusKm / (111.32d * Math.cos(Math.toRadians(lat)));
            venues = venueRepository.searchNearbyActive(qPattern, lat, lng, radiusKm, lat - latitudeDelta,
                    lat + latitudeDelta, lng - longitudeDelta, lng + longitudeDelta, pageable);
        }
        return PageResponse.from(venues, venueMapper::toSummary, request.page(), request.pageSize());
    }

    /**
     * Sửa thông tin địa điểm, thử lại khi xung đột phiên bản ({@code VENUE_011}).
     * Thứ tự kiểm: điện thoại/website từ caller không phải Admin ({@code VENUE_017}, trước mọi truy vấn) → tồn tại
     * ({@code VENUE_001}) → người tạo hoặc Admin ({@code VENUE_013}) → địa điểm ngừng hoạt động chỉ Admin sửa
     * ({@code VENUE_002}) → địa điểm chưa có phường phải gửi phường ({@code VENUE_016}).
     * Admin sửa địa điểm của người khác → ghi audit sau commit.
     */
    @Override
    public VenueResponse updateVenue(UUID venueId, CurrentActor actor, UpdateVenueRequest request) {
        requireAdminForContactFields(actor.admin(), request.phone(), request.website());
        return concurrencyConflictRetrier.execute(ErrorCode.VENUE_011, () -> {
            Venue venue = venueGuard.findVenue(venueId);
            venueGuard.requireOwnerOrAdmin(venue, actor);
            venueGuard.requireWritable(venue, actor);
            if (venue.getWardId() == null && request.wardId() == null) {
                throw new BusinessException(ErrorCode.VENUE_016);
            }
            if (request.wardId() != null) {
                administrativeUnitRepository.findWardWithProvince(request.wardId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.USER_002));
                venue.setWardId(request.wardId());
            }
            if (request.venueName() != null) venue.setName(request.venueName().trim());
            if (request.address() != null) venue.setAddress(request.address().trim());
            if (request.latitude() != null) venue.setLatitude(request.latitude());
            if (request.longitude() != null) venue.setLongitude(request.longitude());
            if (request.venueType() != null) venue.setVenueType(emptyToNull(request.venueType()) == null ? null
                    : Venue.VenueType.valueOf(request.venueType()));
            if (request.phone() != null) venue.setPhone(emptyToNull(request.phone()));
            if (request.website() != null) venue.setWebsite(emptyToNull(request.website()));
            venueRepository.save(venue);
            if (actor.admin() && !actor.userId().equals(venue.getCreatedByUserId())) {
                adminAuditLog.afterCommit(AdminAuditLog.ADMIN_UPDATE_VENUE, actor.userId(), venueId, null);
            }
            // Hibernate tự flush cập nhật của entity đang quản lý trước native projection chi tiết bên dưới.
            return getVenue(venueId);
        });
    }

    /** Q1 venue, Q2 insert, Q3 chốt cảnh báo đóng cửa, chỉ với report CLOSED đầu tiên của một đợt cảnh báo. */
    @Override
    @Transactional
    public VenueReportResponse reportVenue(UUID venueId, CurrentActor actor, ReportVenueRequest request) {
        if (actor.admin()) {
            throw new BusinessException(ErrorCode.VENUE_015);
        }
        Venue venue = venueGuard.activeVenue(venueId);
        VenueReport report;
        try {
            // Flush ngay: unique index report đang chờ là cơ chế chống trùng duy nhất.
            report = venueReportRepository.saveAndFlush(VenueReport.builder()
                    .venueId(venueId)
                    .reporterId(actor.userId())
                    .reason(VenueReport.Reason.valueOf(request.reason()))
                    .detail(request.detail())
                    .build());
        } catch (DataIntegrityViolationException exception) {
            if (DataIntegrityViolations.violates(exception, PENDING_REPORT_UNIQUE_INDEX)) {
                throw new BusinessException(ErrorCode.VENUE_014);
            }
            throw exception;
        }
        if (report.getReason() == VenueReport.Reason.CLOSED && venue.getClosureAlertedAt() == null) {
            OffsetDateTime now = OffsetDateTime.now();
            if (venueRepository.markClosureAlerted(venueId, now) == 1) {
                eventPublisher.publishEvent(new VenueAdminAlertEvent(venueId, venue.getName(),
                        VenueAlertType.CLOSURE_REPORTED, now));
            }
        }
        return venueMapper.toReport(report);
    }

    /**
     * Điện thoại và website chỉ Admin được ghi: caller khác Admin gửi bất kỳ giá trị nào khác {@code null},
     * kể cả chuỗi rỗng để xoá, đều bị từ chối {@code VENUE_017}.
     */
    private static void requireAdminForContactFields(boolean admin, String phone, String website) {
        if (!admin && (phone != null || website != null)) {
            throw new BusinessException(ErrorCode.VENUE_017);
        }
    }

    /** Chuỗi rỗng nghĩa là xoá giá trị; giá trị còn lại được cắt khoảng trắng hai đầu. */
    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value.trim();
    }
}
