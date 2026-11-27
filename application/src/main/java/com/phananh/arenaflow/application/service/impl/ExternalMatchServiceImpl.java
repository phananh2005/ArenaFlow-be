package com.phananh.arenaflow.application.service.impl;

import com.phananh.arenaflow.application.dto.request.CreateExternalMatchRequest;
import com.phananh.arenaflow.application.dto.request.RecruitmentPositionRequest;
import com.phananh.arenaflow.application.dto.request.UpdateExternalMatchRequest;
import com.phananh.arenaflow.application.dto.response.ExternalMatchCancellationResponse;
import com.phananh.arenaflow.application.dto.response.ExternalMatchResponse;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.dto.response.RecruitmentResponse;
import com.phananh.arenaflow.application.event.mercenary.MercenaryGameCancelledEvent;
import com.phananh.arenaflow.application.event.mercenary.MercenaryRecruitmentClosedEvent;
import com.phananh.arenaflow.application.mapper.MapperSupport;
import com.phananh.arenaflow.application.mapper.MercenaryMapper;
import com.phananh.arenaflow.application.service.ExternalMatchService;
import com.phananh.arenaflow.application.service.ExternalMatchStatusFilter;
import com.phananh.arenaflow.application.service.VenueSelectionService;
import com.phananh.arenaflow.application.support.ConcurrencyConflictException;
import com.phananh.arenaflow.application.support.ConcurrencyConflictRetrier;
import com.phananh.arenaflow.application.support.PageRequestFactory;
import com.phananh.arenaflow.application.support.TeamGuard;
import com.phananh.arenaflow.application.support.mercenary.MercenaryGuard;
import com.phananh.arenaflow.application.validation.ExternalMatchSchedule;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.entity.converter.SkillLevelsConverter;
import com.phananh.arenaflow.domain.entity.mercenary.ExternalMatch;
import com.phananh.arenaflow.domain.projection.ExternalMatchContextView;
import com.phananh.arenaflow.domain.projection.ExternalMatchView;
import com.phananh.arenaflow.domain.projection.GameCancelNoticeView;
import com.phananh.arenaflow.domain.projection.RecruitmentRowView;
import com.phananh.arenaflow.domain.projection.RejectedApplicationView;
import com.phananh.arenaflow.domain.projection.SelectableVenueView;
import com.phananh.arenaflow.domain.projection.TeamCaptainContextView;
import com.phananh.arenaflow.domain.repository.ExternalMatchRepository;
import com.phananh.arenaflow.domain.repository.MercenaryApplicationRepository;
import com.phananh.arenaflow.domain.repository.MercenaryRecruitmentRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExternalMatchServiceImpl implements ExternalMatchService {

    private static final SkillLevelsConverter SKILL_LEVELS = new SkillLevelsConverter();

    private final ExternalMatchRepository externalMatchRepository;
    private final MercenaryRecruitmentRepository recruitmentRepository;
    private final MercenaryApplicationRepository applicationRepository;
    private final TeamGuard teamGuard;
    private final MercenaryGuard mercenaryGuard;
    private final VenueSelectionService venueSelectionService;
    private final ConcurrencyConflictRetrier concurrencyConflictRetrier;
    private final MercenaryMapper mercenaryMapper;
    private final PageRequestFactory pageRequestFactory;
    private final ApplicationEventPublisher eventPublisher;
    private final Validator validator;
    private final Clock clock;

    /**
     * Khai báo trận ngoài + tin cho từng vị trí (5 truy vấn): TEAM_001 → TEAM_016 → TEAM_015 → TEAM_002 → VENUE_001 →
     * VENUE_002. Sân khai báo tính là "được chọn" cho cảnh báo địa điểm lâu không dùng.
     */
    @Override
    public ExternalMatchResponse createExternalMatch(UUID teamId, UUID callerId, CreateExternalMatchRequest request) {
        return concurrencyConflictRetrier.execute(ErrorCode.MATCH_014, () -> {
            LocalDateTime now = LocalDateTime.now(clock);
            TeamCaptainContextView team = teamGuard.requireActiveCaptain(teamId, callerId);
            SelectableVenueView venue = venueSelectionService.requireSelectable(List.of(request.venueId())).get(0);
            ExternalMatch match = externalMatchRepository.save(ExternalMatch.builder()
                    .teamId(teamId)
                    .venueId(request.venueId())
                    .matchStartTime(request.startsAt())
                    .matchEndTime(request.endsAt())
                    .skillLevels(request.skillLevels())
                    .opponentName(request.opponentName())
                    .createdByUserId(callerId)
                    .build());
            String[] labels = request.positions().stream().map(RecruitmentPositionRequest::position)
                    .toArray(String[]::new);
            int[] quantities = request.positions().stream().mapToInt(RecruitmentPositionRequest::quantity).toArray();
            // Hibernate tự flush INSERT trận ngoài trước câu native này (khoá ngoại của tin).
            List<RecruitmentRowView> rows = recruitmentRepository.insertAll(null, match.getId(), teamId, callerId,
                    labels, quantities, now);
            venueSelectionService.markSelected(List.of(request.venueId()), now);
            Map<String, RecruitmentRowView> byPosition = rows.stream()
                    .collect(Collectors.toMap(RecruitmentRowView::getPosition, Function.identity()));
            List<RecruitmentResponse> recruitments = Arrays.stream(labels)
                    .map(label -> mercenaryMapper.toCreatedExternalRecruitment(match, venue, team,
                            byPosition.get(label)))
                    .toList();
            return mercenaryMapper.toCreatedExternalMatch(match, venue, recruitments);
        });
    }

    /** Trận ngoài của đội (đội trưởng; đội INACTIVE vẫn xem): 3 truy vấn. */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<ExternalMatchResponse> listTeamExternalMatches(UUID teamId, UUID callerId,
                                                                       ExternalMatchStatusFilter status, int page,
                                                                       int pageSize) {
        teamGuard.requireCaptainAllowInactive(teamId, callerId);
        Page<ExternalMatchView> rows = externalMatchRepository.findByTeam(teamId, callerId, status.toQueryValue(),
                LocalDateTime.now(clock), pageRequestFactory.create(page, pageSize));
        return PageResponse.from(rows, mercenaryMapper::toExternalMatch, page, pageSize);
    }

    /** Chi tiết trận ngoài (1 truy vấn): MERCENARY_024 → TEAM_016 → TEAM_002 (đội INACTIVE được). */
    @Override
    @Transactional(readOnly = true)
    public ExternalMatchResponse getExternalMatch(UUID externalMatchId, UUID callerId) {
        ExternalMatchView view = externalMatchRepository.findDetail(externalMatchId, callerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MERCENARY_024));
        mercenaryGuard.requireCaptain(view.getTeamStatus(), Boolean.TRUE.equals(view.getCallerCaptain()), true);
        return mercenaryMapper.toExternalMatch(view);
    }

    /**
     * Sửa trận ngoài (2–5 truy vấn): MERCENARY_024 → TEAM_016 → TEAM_015 → TEAM_002 → MERCENARY_004 (đã huỷ / qua giờ
     * kết thúc) → (đổi sân / giờ / trình độ) MERCENARY_013 → MERCENARY_025 → VENUE_001 → VENUE_002. CAS {@code version}
     * khoá trận ngoài trước, rồi khoá mọi tin của trận và kiểm lại số đã duyệt. Chỉ đổi tên đối thủ → không khoá tin,
     * không đọc sân. Không đổi gì → trả dữ liệu hiện tại.
     */
    @Override
    public ExternalMatchResponse updateExternalMatch(UUID externalMatchId, UUID callerId,
                                                     UpdateExternalMatchRequest request) {
        return concurrencyConflictRetrier.execute(ErrorCode.MATCH_014, () -> {
            LocalDateTime now = LocalDateTime.now(clock);
            ExternalMatchContextView ctx = context(externalMatchId, callerId);
            mercenaryGuard.requireCaptain(ctx.getTeamStatus(), Boolean.TRUE.equals(ctx.getCallerCaptain()), false);
            requireNotFinished(ctx, now);
            UUID venueId = request.venueId() != null ? request.venueId() : ctx.getVenueId();
            LocalDateTime start = request.startsAt() != null ? request.startsAt() : ctx.getStartsAt();
            LocalDateTime end = request.endsAt() != null ? request.endsAt() : ctx.getEndsAt();
            List<String> currentSkills = MapperSupport.skillLevels(ctx.getSkillLevels());
            List<String> skills = request.skillLevels() != null ? request.skillLevels() : currentSkills;
            String opponent = request.opponentName() == null ? ctx.getOpponentName()
                    : request.opponentName().isEmpty() ? null : request.opponentName();
            boolean venueChanged = !venueId.equals(ctx.getVenueId());
            boolean timeChanged = !start.equals(ctx.getStartsAt()) || !end.equals(ctx.getEndsAt());
            boolean coreChanged = venueChanged || timeChanged || !skills.equals(currentSkills);
            if (!coreChanged && Objects.equals(opponent, ctx.getOpponentName())) {
                return mercenaryMapper.toExternalMatch(ctx);
            }
            if (timeChanged) {
                requireValidSchedule(start, end);
            }
            SelectableVenueView newVenue = null;
            if (coreChanged) {
                mercenaryGuard.requireNotStarted(ctx.getStartsAt(), now);
                if (Boolean.TRUE.equals(ctx.getHasApprovedMercenary())) {
                    throw new BusinessException(ErrorCode.MERCENARY_025);
                }
                if (venueChanged) {
                    newVenue = venueSelectionService.requireSelectable(List.of(venueId)).get(0);
                }
            }
            if (externalMatchRepository.updateIfVersion(externalMatchId, ctx.getVersion(), venueId, start, end,
                    SKILL_LEVELS.convertToDatabaseColumn(skills), opponent, now) == 0) {
                throw new ConcurrencyConflictException();
            }
            if (coreChanged) {
                // Khoá mọi tin của trận sau khi đã khoá trận ngoài; đọc lại số đã duyệt sau khi chờ khoá.
                int approved = recruitmentRepository.lockByExternalMatch(externalMatchId, now).stream()
                        .mapToInt(Integer::intValue).sum();
                if (approved > 0) {
                    throw new BusinessException(ErrorCode.MERCENARY_025);
                }
            }
            if (venueChanged) {
                venueSelectionService.markSelected(List.of(venueId), now);
            }
            return mercenaryMapper.toUpdatedExternalMatch(ctx, newVenue, start, end, skills, opponent, now);
        });
    }

    /**
     * Huỷ trận ngoài (5 truy vấn): MERCENARY_024 → TEAM_016 → TEAM_002 (đội INACTIVE được) → MERCENARY_004 →
     * MERCENARY_013 (chỉ trước giờ bắt đầu). Đóng mọi tin đang mở, từ chối đơn chờ, đánh dấu đã báo cho mọi mercenary đã
     * duyệt (kể cả của tin đã đóng trước đó). Không ghi uy tín.
     */
    @Override
    public ExternalMatchCancellationResponse cancelExternalMatch(UUID externalMatchId, UUID callerId) {
        return concurrencyConflictRetrier.execute(ErrorCode.MATCH_014, () -> {
            LocalDateTime now = LocalDateTime.now(clock);
            ExternalMatchContextView ctx = context(externalMatchId, callerId);
            mercenaryGuard.requireCaptain(ctx.getTeamStatus(), Boolean.TRUE.equals(ctx.getCallerCaptain()), true);
            requireNotFinished(ctx, now);
            mercenaryGuard.requireNotStarted(ctx.getStartsAt(), now);
            if (externalMatchRepository.cancelIfScheduled(externalMatchId, now) == 0) {
                throw new ConcurrencyConflictException();
            }
            List<UUID> closed = recruitmentRepository.closeActiveByExternalMatch(externalMatchId, now);
            List<RejectedApplicationView> rejected = closed.isEmpty() ? List.of()
                    : applicationRepository.rejectPendingByRecruitmentIds(closed.toArray(UUID[]::new), now);
            List<GameCancelNoticeView> notices =
                    applicationRepository.markGameCancelNotifiedByExternalMatch(externalMatchId, now);
            Map<UUID, List<UUID>> rejectedByRecruitment = rejected.stream().collect(Collectors.groupingBy(
                    RejectedApplicationView::getRecruitmentId,
                    Collectors.mapping(RejectedApplicationView::getId, Collectors.toList())));
            for (UUID recruitmentId : closed) {
                eventPublisher.publishEvent(new MercenaryRecruitmentClosedEvent(recruitmentId, null, externalMatchId,
                        ctx.getTeamId(), MercenaryRecruitmentClosedEvent.Cause.EXTERNAL_MATCH_CANCELLED,
                        List.copyOf(rejectedByRecruitment.getOrDefault(recruitmentId, List.of())), now));
            }
            if (!notices.isEmpty()) {
                eventPublisher.publishEvent(new MercenaryGameCancelledEvent(null, externalMatchId,
                        notices.stream().map(GameCancelNoticeView::getId).toList(), now));
            }
            return new ExternalMatchCancellationResponse(externalMatchId, ExternalMatch.Status.CANCELLED.name(), now,
                    closed.size(), rejected.size());
        });
    }

    private ExternalMatchContextView context(UUID externalMatchId, UUID callerId) {
        return externalMatchRepository.findContext(externalMatchId, callerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MERCENARY_024));
    }

    /** Trận đã huỷ hoặc đã qua giờ kết thúc → MERCENARY_004. */
    private static void requireNotFinished(ExternalMatchView ctx, LocalDateTime now) {
        if (ExternalMatch.Status.CANCELLED.name().equals(ctx.getStatus()) || !now.isBefore(ctx.getEndsAt())) {
            throw new BusinessException(ErrorCode.MERCENARY_004);
        }
    }

    /** Cặp giờ sau khi áp thay đổi theo cùng quy tắc lúc khai báo; sai → 400 VALIDATION_ERROR gắn field. */
    private void requireValidSchedule(LocalDateTime start, LocalDateTime end) {
        Set<ConstraintViolation<ExternalMatchSchedule>> violations =
                validator.validate(new ExternalMatchSchedule(start, end));
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }
}
