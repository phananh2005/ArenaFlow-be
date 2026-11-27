package com.phananh.arenaflow.application.service.impl;

import com.phananh.arenaflow.application.dto.request.AddRecruitmentsRequest;
import com.phananh.arenaflow.application.dto.request.CreateRecruitmentsRequest;
import com.phananh.arenaflow.application.dto.request.RecruitmentPositionRequest;
import com.phananh.arenaflow.application.dto.request.SearchRecruitmentsRequest;
import com.phananh.arenaflow.application.dto.request.UpdateRecruitmentRequest;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.dto.response.RecruitmentResponse;
import com.phananh.arenaflow.application.event.mercenary.MercenaryRecruitmentClosedEvent;
import com.phananh.arenaflow.application.mapper.MercenaryMapper;
import com.phananh.arenaflow.application.service.MercenaryRecruitmentService;
import com.phananh.arenaflow.application.service.RecruitmentStatusFilter;
import com.phananh.arenaflow.application.support.ConcurrencyConflictException;
import com.phananh.arenaflow.application.support.ConcurrencyConflictRetrier;
import com.phananh.arenaflow.application.support.DataIntegrityViolations;
import com.phananh.arenaflow.application.support.GeoBoundingBox;
import com.phananh.arenaflow.application.support.PageRequestFactory;
import com.phananh.arenaflow.application.support.TeamGuard;
import com.phananh.arenaflow.application.support.mercenary.MercenaryGuard;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.projection.ProfileAreaView;
import com.phananh.arenaflow.domain.projection.RecruitContextView;
import com.phananh.arenaflow.domain.projection.RecruitmentRowView;
import com.phananh.arenaflow.domain.projection.RecruitmentView;
import com.phananh.arenaflow.domain.projection.RecruitmentWriteContextView;
import com.phananh.arenaflow.domain.repository.ExternalMatchRepository;
import com.phananh.arenaflow.domain.repository.MercenaryApplicationRepository;
import com.phananh.arenaflow.domain.repository.MercenaryRecruitmentRepository;
import com.phananh.arenaflow.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MercenaryRecruitmentServiceImpl implements MercenaryRecruitmentService {

    private static final Set<String> RECRUITMENT_POSITION_INDEXES = Set.of(
            "idx_mercenary_recruitment_match_position", "idx_mercenary_recruitment_external_position");

    private final MercenaryRecruitmentRepository recruitmentRepository;
    private final MercenaryApplicationRepository applicationRepository;
    private final ExternalMatchRepository externalMatchRepository;
    private final UserRepository userRepository;
    private final TeamGuard teamGuard;
    private final MercenaryGuard mercenaryGuard;
    private final ConcurrencyConflictRetrier concurrencyConflictRetrier;
    private final MercenaryMapper mercenaryMapper;
    private final PageRequestFactory pageRequestFactory;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * Tạo một tin mỗi vị trí cho kèo đã ghép (2 truy vấn). Thứ tự kiểm: MATCH_001 → TEAM_001 → TEAM_016 → TEAM_015 →
     * TEAM_002 → MATCH_009 → MERCENARY_003 → MERCENARY_004 → MERCENARY_013 → MERCENARY_023 (vị trí đã có tin đang mở;
     * tạo đồng thời → unique index).
     */
    @Override
    public List<RecruitmentResponse> createRecruitments(UUID matchId, UUID callerId, CreateRecruitmentsRequest request) {
        return concurrencyConflictRetrier.execute(ErrorCode.MATCH_014, () -> {
            LocalDateTime now = LocalDateTime.now(clock);
            RecruitContextView ctx = recruitmentRepository.findRecruitContext(matchId, request.teamId(), callerId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.MATCH_001));
            if (ctx.getTeamId() == null) {
                throw new BusinessException(ErrorCode.TEAM_001);
            }
            return insertRecruitments(ctx, callerId, request.positions(), now);
        });
    }

    /**
     * Thêm vị trí cho trận ngoài hệ thống (2 truy vấn). Thứ tự kiểm: MERCENARY_024 → TEAM_016 → TEAM_015 → TEAM_002 →
     * MERCENARY_004 → MERCENARY_013 → MERCENARY_023.
     */
    @Override
    public List<RecruitmentResponse> addExternalRecruitments(UUID externalMatchId, UUID callerId,
                                                             AddRecruitmentsRequest request) {
        return concurrencyConflictRetrier.execute(ErrorCode.MATCH_014, () -> {
            LocalDateTime now = LocalDateTime.now(clock);
            RecruitContextView ctx = externalMatchRepository.findRecruitContext(externalMatchId, callerId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.MERCENARY_024));
            return insertRecruitments(ctx, callerId, request.positions(), now);
        });
    }

    /** Chung cho kèo và trận ngoài: guard đội + trận + vị trí rồi một câu INSERT cho mọi vị trí. */
    private List<RecruitmentResponse> insertRecruitments(RecruitContextView ctx, UUID callerId,
                                                 List<RecruitmentPositionRequest> positions, LocalDateTime now) {
        mercenaryGuard.requireCaptain(ctx.getTeamStatus(), Boolean.TRUE.equals(ctx.getCallerCaptain()), false);
        mercenaryGuard.requireRecruitableMatch(ctx, now);
        Set<String> active = ctx.getActivePositions() == null || ctx.getActivePositions().isEmpty() ? Set.of()
                : Set.of(ctx.getActivePositions().split("\\|"));
        if (positions.stream().map(RecruitmentPositionRequest::position).anyMatch(active::contains)) {
            throw new BusinessException(ErrorCode.MERCENARY_023);
        }
        String[] labels = positions.stream().map(RecruitmentPositionRequest::position).toArray(String[]::new);
        int[] quantities = positions.stream().mapToInt(RecruitmentPositionRequest::quantity).toArray();
        List<RecruitmentRowView> rows;
        try {
            rows = recruitmentRepository.insertAll(ctx.getMatchId(), ctx.getExternalMatchId(), ctx.getTeamId(),
                    callerId, labels, quantities, now);
        } catch (DataIntegrityViolationException exception) {
            if (RECRUITMENT_POSITION_INDEXES.contains(DataIntegrityViolations.constraintName(exception))) {
                throw new BusinessException(ErrorCode.MERCENARY_023);
            }
            throw exception;
        }
        Map<String, RecruitmentRowView> byPosition = rows.stream()
                .collect(Collectors.toMap(RecruitmentRowView::getPosition, Function.identity()));
        return Arrays.stream(labels).map(label -> mercenaryMapper.toCreated(ctx, byPosition.get(label))).toList();
    }

    /** Tin của đội (đội trưởng; đội INACTIVE vẫn xem): 3 truy vấn. */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<RecruitmentResponse> listTeamRecruitments(UUID teamId, UUID callerId, UUID matchId,
                                                                  RecruitmentStatusFilter status, int page,
                                                                  int pageSize) {
        teamGuard.requireCaptainAllowInactive(teamId, callerId);
        Page<RecruitmentView> rows = recruitmentRepository.findByTeam(teamId, callerId, matchId, status.toQueryValue(),
                LocalDateTime.now(clock), pageRequestFactory.create(page, pageSize));
        return PageResponse.from(rows, mercenaryMapper::toResponse, page, pageSize);
    }

    /**
     * Tìm kèo mercenary: có toạ độ → theo bán kính (2 truy vấn); không → theo tỉnh của hồ sơ, sân cùng phường lên đầu
     * (3 truy vấn; tài khoản đã xoá → USER_001; hồ sơ chưa có phường → MERCENARY_018). Toạ độ không lưu, không log.
     */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<RecruitmentResponse> search(UUID callerId, SearchRecruitmentsRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        Pageable pageable = pageRequestFactory.create(request.page(), request.pageSize());
        Page<RecruitmentView> rows;
        if (request.lat() != null) {
            double lat = request.lat();
            double lng = request.lng();
            double radiusKm = request.radiusKm();
            GeoBoundingBox box = GeoBoundingBox.of(lat, lng, radiusKm);
            rows = recruitmentRepository.searchNearby(callerId, request.position(), now, lat, lng, radiusKm,
                    box.minLat(), box.maxLat(), box.minLng(), box.maxLng(), pageable);
        } else {
            ProfileAreaView area = userRepository.findProfileArea(callerId)
                    .filter(view -> !Boolean.TRUE.equals(view.getDeleted()))
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_001));
            if (area.getWardId() == null) {
                throw new BusinessException(ErrorCode.MERCENARY_018);
            }
            rows = recruitmentRepository.searchByProvince(callerId, request.position(), now, area.getProvinceId(),
                    area.getWardId(), pageable);
        }
        return PageResponse.from(rows, mercenaryMapper::toResponse, request.page(), request.pageSize());
    }

    /** Chi tiết tin (1 truy vấn); ẩn / hiện sân theo người xem. */
    @Override
    @Transactional(readOnly = true)
    public RecruitmentResponse getRecruitment(UUID recruitmentId, UUID callerId, Double lat, Double lng) {
        return recruitmentRepository.findView(recruitmentId, callerId, LocalDateTime.now(clock), lat, lng)
                .map(mercenaryMapper::toResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.MERCENARY_001));
    }

    /**
     * Sửa số lượng (2 truy vấn): MERCENARY_001 → TEAM_016 → TEAM_015 → TEAM_002 → MERCENARY_007 → MERCENARY_008 (thấp hơn
     * số đã duyệt) → MERCENARY_013 (tăng khi trận đã bắt đầu). Bằng số hiện tại → không ghi. CAS 0 dòng → thử lại.
     */
    @Override
    public RecruitmentResponse updateQuantity(UUID recruitmentId, UUID callerId, UpdateRecruitmentRequest request) {
        return concurrencyConflictRetrier.execute(ErrorCode.MATCH_014, () -> {
            LocalDateTime now = LocalDateTime.now(clock);
            RecruitmentWriteContextView ctx = writeContext(recruitmentId, callerId, now);
            mercenaryGuard.requireCaptain(ctx.getTeamStatus(), Boolean.TRUE.equals(ctx.getViewerIsCaptain()), false);
            mercenaryGuard.requireActive(ctx.getEffectiveStatus());
            int quantity = request.quantityNeeded();
            if (quantity < ctx.getQuantityApproved()) {
                throw new BusinessException(ErrorCode.MERCENARY_008);
            }
            if (quantity > ctx.getQuantityNeeded()) {
                mercenaryGuard.requireNotStarted(ctx.getGameStart(), now);
            }
            if (quantity == ctx.getQuantityNeeded()) {
                return mercenaryMapper.toResponse(ctx);
            }
            if (recruitmentRepository.updateQuantity(recruitmentId, quantity, ctx.getQuantityApproved(),
                    ctx.getQuantityNeeded(), now) == 0) {
                throw new ConcurrencyConflictException();
            }
            String status = quantity == ctx.getQuantityApproved() ? "FILLED" : "OPEN";
            return mercenaryMapper.toUpdated(ctx, quantity, status, now);
        });
    }

    /**
     * Đóng tin (3 truy vấn): MERCENARY_001 → TEAM_016 → TEAM_002 (đội INACTIVE được) → MERCENARY_007. Đơn chờ bị từ chối
     * cùng transaction; phát event kèm id đơn bị từ chối.
     */
    @Override
    public RecruitmentResponse close(UUID recruitmentId, UUID callerId) {
        return concurrencyConflictRetrier.execute(ErrorCode.MATCH_014, () -> {
            LocalDateTime now = LocalDateTime.now(clock);
            RecruitmentWriteContextView ctx = writeContext(recruitmentId, callerId, now);
            mercenaryGuard.requireCaptain(ctx.getTeamStatus(), Boolean.TRUE.equals(ctx.getViewerIsCaptain()), true);
            mercenaryGuard.requireActive(ctx.getEffectiveStatus());
            if (recruitmentRepository.closeIfActive(recruitmentId, now) == 0) {
                throw new ConcurrencyConflictException();
            }
            List<UUID> rejected = applicationRepository.rejectPendingByRecruitment(recruitmentId, now);
            eventPublisher.publishEvent(new MercenaryRecruitmentClosedEvent(recruitmentId, ctx.getMatchId(),
                    ctx.getExternalMatchId(), ctx.getTeamId(), MercenaryRecruitmentClosedEvent.Cause.CAPTAIN,
                    List.copyOf(rejected), now));
            return mercenaryMapper.toClosed(ctx, now, rejected.size());
        });
    }

    private RecruitmentWriteContextView writeContext(UUID recruitmentId, UUID callerId, LocalDateTime now) {
        return recruitmentRepository.findWriteContext(recruitmentId, callerId, now)
                .orElseThrow(() -> new BusinessException(ErrorCode.MERCENARY_001));
    }
}
