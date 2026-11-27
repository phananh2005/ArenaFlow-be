package com.phananh.arenaflow.application.service.impl;

import com.phananh.arenaflow.application.dto.request.WithdrawApplicationRequest;
import com.phananh.arenaflow.application.dto.response.ApplicantResponse;
import com.phananh.arenaflow.application.dto.response.ApplicationResponse;
import com.phananh.arenaflow.application.dto.response.ApprovalResponse;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.dto.response.RecruitmentCountResponse;
import com.phananh.arenaflow.application.dto.response.WithdrawalResponse;
import com.phananh.arenaflow.application.event.mercenary.MercenaryApplicationApprovedEvent;
import com.phananh.arenaflow.application.event.mercenary.MercenaryApplicationRejectedEvent;
import com.phananh.arenaflow.application.event.mercenary.MercenaryApplicationSubmittedEvent;
import com.phananh.arenaflow.application.event.mercenary.MercenaryApplicationWithdrawnEvent;
import com.phananh.arenaflow.application.mapper.MercenaryMapper;
import com.phananh.arenaflow.application.service.ApplicationStatusFilter;
import com.phananh.arenaflow.application.service.MercenaryApplicationService;
import com.phananh.arenaflow.application.service.ReliabilityChange;
import com.phananh.arenaflow.application.service.ReliabilityReason;
import com.phananh.arenaflow.application.service.ReputationService;
import com.phananh.arenaflow.application.support.ConcurrencyConflictException;
import com.phananh.arenaflow.application.support.ConcurrencyConflictRetrier;
import com.phananh.arenaflow.application.support.DataIntegrityViolations;
import com.phananh.arenaflow.application.support.PageRequestFactory;
import com.phananh.arenaflow.application.support.chat.ChatRealtimeGateway;
import com.phananh.arenaflow.application.support.chat.RealtimeFrame;
import com.phananh.arenaflow.application.support.mercenary.MercenaryGuard;
import com.phananh.arenaflow.application.support.reputation.ReputationRules;
import com.phananh.arenaflow.application.support.reputation.TimeBandPenaltyPolicy;
import com.phananh.arenaflow.application.support.reputation.VerificationRefresher;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.entity.chat.Conversation;
import com.phananh.arenaflow.domain.entity.mercenary.MercenaryApplication;
import com.phananh.arenaflow.domain.entity.mercenary.MercenaryRecruitment;
import com.phananh.arenaflow.domain.projection.ApplicationContextView;
import com.phananh.arenaflow.domain.projection.ApplyContextView;
import com.phananh.arenaflow.domain.projection.RecruitmentWriteContextView;
import com.phananh.arenaflow.domain.repository.ConversationRepository;
import com.phananh.arenaflow.domain.repository.MercenaryApplicationRepository;
import com.phananh.arenaflow.domain.repository.MercenaryRecruitmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MercenaryApplicationServiceImpl implements MercenaryApplicationService {

    private static final String ACTIVE_APPLICATION_INDEX = "idx_mercenary_application_active";
    private static final String PENDING = "PENDING";
    private static final String APPROVED = "APPROVED";
    private static final String FILLED = "FILLED";

    private final MercenaryApplicationRepository applicationRepository;
    private final MercenaryRecruitmentRepository recruitmentRepository;
    private final ConversationRepository conversationRepository;
    private final MercenaryGuard mercenaryGuard;
    private final ReputationService reputationService;
    private final VerificationRefresher verificationRefresher;
    private final ChatRealtimeGateway realtimeGateway;
    private final ConcurrencyConflictRetrier concurrencyConflictRetrier;
    private final MercenaryMapper mercenaryMapper;
    private final PageRequestFactory pageRequestFactory;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * Ứng tuyển + mở cuộc trò chuyện của đơn (3 truy vấn). Thứ tự kiểm: USER_001 (tài khoản đã xoá) → MERCENARY_001 →
     * MERCENARY_007 → MERCENARY_013 → MERCENARY_006 → MERCENARY_005 (đội tuyển không ACTIVE) → MERCENARY_010 →
     * MERCENARY_009 (đã có đơn đang hoạt động; nộp đồng thời → unique index) → MERCENARY_011.
     */
    @Override
    public ApplicationResponse apply(UUID recruitmentId, UUID callerId) {
        return concurrencyConflictRetrier.execute(ErrorCode.MATCH_014, () -> {
            LocalDateTime now = LocalDateTime.now(clock);
            ApplyContextView ctx = applicationRepository.findApplyContext(recruitmentId, callerId, now)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_001));
            mercenaryGuard.requireNotDeleted(Boolean.TRUE.equals(ctx.getCallerDeleted()));
            if (ctx.getRecruitmentId() == null) {
                throw new BusinessException(ErrorCode.MERCENARY_001);
            }
            mercenaryGuard.requireActive(ctx.getEffectiveStatus());
            mercenaryGuard.requireNotStarted(ctx.getGameStart(), now);
            mercenaryGuard.requireOpen(ctx.getEffectiveStatus());
            if (!"ACTIVE".equals(ctx.getTeamStatus())) {
                throw new BusinessException(ErrorCode.MERCENARY_005);
            }
            if (Boolean.TRUE.equals(ctx.getCallerMember())) {
                throw new BusinessException(ErrorCode.MERCENARY_010);
            }
            if (Boolean.TRUE.equals(ctx.getCallerHasActive())) {
                throw new BusinessException(ErrorCode.MERCENARY_009);
            }
            mercenaryGuard.requireEligibleApplicant(false, Boolean.TRUE.equals(ctx.getCallerOverlap()));
            MercenaryApplication application = saveApplication(MercenaryApplication.builder()
                    .recruitmentId(recruitmentId)
                    .applicantId(callerId)
                    .appliedAt(now)
                    .build());
            Conversation conversation = conversationRepository.save(Conversation.builder()
                    .contextType(Conversation.ContextType.MERCENARY_APPLICATION)
                    .applicationId(application.getId())
                    .matchId(ctx.getMatchId())
                    .externalMatchId(ctx.getExternalMatchId())
                    .partyAType(Conversation.PartyType.TEAM)
                    .partyAId(ctx.getTeamId())
                    .partyBType(Conversation.PartyType.USER)
                    .partyBId(callerId)
                    .build());
            eventPublisher.publishEvent(new MercenaryApplicationSubmittedEvent(application.getId(), recruitmentId,
                    ctx.getMatchId(), ctx.getExternalMatchId(), ctx.getTeamId(), callerId, now));
            return mercenaryMapper.toApplied(ctx, callerId, application, conversation.getId());
        });
    }

    /** Flush ngay để đổi vi phạm unique index đơn đang hoạt động thành MERCENARY_009 trước khi tạo cuộc trò chuyện. */
    private MercenaryApplication saveApplication(MercenaryApplication application) {
        try {
            return applicationRepository.saveAndFlush(application);
        } catch (DataIntegrityViolationException exception) {
            if (DataIntegrityViolations.violates(exception, ACTIVE_APPLICATION_INDEX)) {
                throw new BusinessException(ErrorCode.MERCENARY_009);
            }
            throw exception;
        }
    }

    /**
     * Ứng viên của tin (đội trưởng; đội INACTIVE vẫn xem) kèm uy tín công khai — 3 truy vấn: MERCENARY_001 → TEAM_016 →
     * TEAM_002.
     */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<ApplicantResponse> listApplicants(UUID recruitmentId, UUID callerId,
                                                          ApplicationStatusFilter status, int page, int pageSize) {
        LocalDateTime now = LocalDateTime.now(clock);
        RecruitmentWriteContextView ctx = recruitmentRepository.findWriteContext(recruitmentId, callerId, now)
                .orElseThrow(() -> new BusinessException(ErrorCode.MERCENARY_001));
        mercenaryGuard.requireCaptain(ctx.getTeamStatus(), Boolean.TRUE.equals(ctx.getViewerIsCaptain()), true);
        return PageResponse.from(applicationRepository.findApplicants(recruitmentId, status.toQueryValue(), now,
                        ReputationRules.CANCEL_WINDOW_HOURS, pageRequestFactory.create(page, pageSize)),
                mercenaryMapper::toApplicant, page, pageSize);
    }

    /** Đơn của chính người gọi (2 truy vấn); đơn APPROVED có sân và tên đội trưởng. */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<ApplicationResponse> listMyApplications(UUID callerId, ApplicationStatusFilter status,
                                                                int page, int pageSize) {
        return PageResponse.from(applicationRepository.findByApplicant(callerId, status.toQueryValue(),
                        LocalDateTime.now(clock), pageRequestFactory.create(page, pageSize)),
                view -> mercenaryMapper.toApplication(view, false), page, pageSize);
    }

    /** Chi tiết đơn (1 truy vấn): MERCENARY_002 → MERCENARY_014 (không phải ứng viên / đội trưởng ACTIVE hiện tại). */
    @Override
    @Transactional(readOnly = true)
    public ApplicationResponse getApplication(UUID applicationId, UUID callerId) {
        ApplicationContextView ctx = context(applicationId, callerId, LocalDateTime.now(clock));
        boolean captain = Boolean.TRUE.equals(ctx.getCallerCaptain());
        if (!captain && !Boolean.TRUE.equals(ctx.getCallerApplicant())) {
            throw new BusinessException(ErrorCode.MERCENARY_014);
        }
        return mercenaryMapper.toApplication(ctx, captain);
    }

    /**
     * Duyệt (3 truy vấn): MERCENARY_002 → TEAM_016 → TEAM_015 → TEAM_002 → MERCENARY_007 → MERCENARY_012 (đơn không
     * còn chờ) → MERCENARY_013 → MERCENARY_006 → MERCENARY_010 → MERCENARY_011. Khoá tin (CAS số đã duyệt) rồi đơn;
     * bất kỳ câu nào 0 dòng → thử lại. Frame realtime sau commit.
     */
    @Override
    public ApprovalResponse approve(UUID applicationId, UUID callerId) {
        return concurrencyConflictRetrier.execute(ErrorCode.MATCH_014, () -> {
            LocalDateTime now = LocalDateTime.now(clock);
            ApplicationContextView ctx = context(applicationId, callerId, now);
            mercenaryGuard.requireCaptain(ctx.getTeamStatus(), Boolean.TRUE.equals(ctx.getCallerCaptain()), false);
            mercenaryGuard.requireActive(ctx.getRecruitmentEffectiveStatus());
            requireStatus(ctx.getEffectiveStatus(), PENDING);
            mercenaryGuard.requireNotStarted(ctx.getGameStart(), now);
            mercenaryGuard.requireOpen(ctx.getRecruitmentEffectiveStatus());
            mercenaryGuard.requireEligibleApplicant(Boolean.TRUE.equals(ctx.getApplicantMember()),
                    Boolean.TRUE.equals(ctx.getApplicantOverlap()));
            if (recruitmentRepository.incrementApproved(ctx.getRecruitmentId(), ctx.getQuantityApproved(),
                    ctx.getQuantityNeeded(), now) == 0) {
                throw new ConcurrencyConflictException();
            }
            if (applicationRepository.approveIfPending(applicationId, now) == 0) {
                throw new ConcurrencyConflictException();
            }
            int approved = ctx.getQuantityApproved() + 1;
            MercenaryRecruitment.Status recruitmentStatus = approved == ctx.getQuantityNeeded()
                    ? MercenaryRecruitment.Status.FILLED : MercenaryRecruitment.Status.OPEN;
            pushUpdate(ctx, MercenaryApplication.Status.APPROVED, recruitmentStatus, approved);
            eventPublisher.publishEvent(new MercenaryApplicationApprovedEvent(applicationId, ctx.getRecruitmentId(),
                    ctx.getMatchId(), ctx.getExternalMatchId(), ctx.getTeamId(), ctx.getApplicantId(), now));
            return new ApprovalResponse(applicationId, APPROVED, now, new RecruitmentCountResponse(
                    ctx.getRecruitmentId(), recruitmentStatus.name(), ctx.getQuantityNeeded(), approved));
        });
    }

    /**
     * Từ chối (2 truy vấn): MERCENARY_002 → TEAM_016 → TEAM_002 (đội INACTIVE được) → MERCENARY_007 → MERCENARY_012.
     */
    @Override
    public void reject(UUID applicationId, UUID callerId) {
        concurrencyConflictRetrier.execute(ErrorCode.MATCH_014, () -> {
            LocalDateTime now = LocalDateTime.now(clock);
            ApplicationContextView ctx = context(applicationId, callerId, now);
            mercenaryGuard.requireCaptain(ctx.getTeamStatus(), Boolean.TRUE.equals(ctx.getCallerCaptain()), true);
            mercenaryGuard.requireActive(ctx.getRecruitmentEffectiveStatus());
            requireStatus(ctx.getEffectiveStatus(), PENDING);
            if (applicationRepository.rejectIfPending(applicationId, now) == 0) {
                throw new ConcurrencyConflictException();
            }
            pushUpdate(ctx, MercenaryApplication.Status.REJECTED,
                    MercenaryRecruitment.Status.valueOf(ctx.getRecruitmentEffectiveStatus()), ctx.getQuantityApproved());
            eventPublisher.publishEvent(new MercenaryApplicationRejectedEvent(applicationId, ctx.getRecruitmentId(),
                    ctx.getMatchId(), ctx.getExternalMatchId(), ctx.getTeamId(), ctx.getApplicantId(),
                    MercenaryApplicationRejectedEvent.Cause.CAPTAIN, now));
            return null;
        });
    }

    /**
     * Rút đơn (PENDING: 2 truy vấn; APPROVED: 5 truy vấn kể cả refresh huy hiệu sau commit). Thứ tự kiểm: MERCENARY_002 →
     * MERCENARY_014 (không phải ứng viên) → MERCENARY_012 (trạng thái hiệu lực khác {@code expectedStatus}) → với
     * APPROVED: MERCENARY_004 (trận huỷ / đã kết thúc) → MERCENARY_013. Rút lui sau duyệt: bớt số đã duyệt của tin trước
     * (khoá tin trước đơn), trừ điểm theo khung giờ và cắt chuỗi trận sạch.
     */
    @Override
    public WithdrawalResponse withdraw(UUID applicationId, UUID callerId, WithdrawApplicationRequest request) {
        return concurrencyConflictRetrier.execute(ErrorCode.MATCH_014, () -> {
            LocalDateTime now = LocalDateTime.now(clock);
            ApplicationContextView ctx = context(applicationId, callerId, now);
            if (!Boolean.TRUE.equals(ctx.getCallerApplicant())) {
                throw new BusinessException(ErrorCode.MERCENARY_014);
            }
            requireStatus(ctx.getEffectiveStatus(), request.expectedStatus());
            int penalty = 0;
            int approved = ctx.getQuantityApproved();
            String recruitmentStatus = ctx.getRecruitmentEffectiveStatus();
            if (APPROVED.equals(request.expectedStatus())) {
                if (Boolean.TRUE.equals(ctx.getGameCancelled()) || Boolean.TRUE.equals(ctx.getGameEnded())) {
                    throw new BusinessException(ErrorCode.MERCENARY_004);
                }
                mercenaryGuard.requireNotStarted(ctx.getGameStart(), now);
                if (recruitmentRepository.decrementApproved(ctx.getRecruitmentId(), now) == 0) {
                    throw new IllegalStateException("Recruitment of approved application not found");
                }
                if (applicationRepository.withdrawApproved(applicationId, now) == 0) {
                    throw new ConcurrencyConflictException();
                }
                int delta = TimeBandPenaltyPolicy.MERCENARY_WITHDRAWAL.penaltyFor(ctx.getGameStart(), now);
                reputationService.adjustUserReliability(callerId, new ReliabilityChange(delta,
                        ReliabilityReason.MERCENARY_WITHDRAWN_AFTER_APPROVED, applicationId, true));
                verificationRefresher.refreshUsersAfterCommit(Set.of(callerId));
                penalty = -delta;
                approved -= 1;
                recruitmentStatus = FILLED.equals(recruitmentStatus) ? "OPEN" : recruitmentStatus;
            } else if (applicationRepository.withdrawPending(applicationId, now) == 0) {
                throw new ConcurrencyConflictException();
            }
            MercenaryApplication.Status previous = MercenaryApplication.Status.valueOf(request.expectedStatus());
            pushUpdate(ctx, MercenaryApplication.Status.WITHDRAWN, MercenaryRecruitment.Status.valueOf(recruitmentStatus),
                    approved);
            eventPublisher.publishEvent(new MercenaryApplicationWithdrawnEvent(applicationId, ctx.getRecruitmentId(),
                    ctx.getMatchId(), ctx.getExternalMatchId(), ctx.getTeamId(), callerId, previous,
                    MercenaryApplicationWithdrawnEvent.Cause.APPLICANT, now));
            return new WithdrawalResponse(applicationId, MercenaryApplication.Status.WITHDRAWN.name(),
                    request.expectedStatus(), now, penalty, new RecruitmentCountResponse(ctx.getRecruitmentId(),
                    recruitmentStatus, ctx.getQuantityNeeded(), approved));
        });
    }

    private ApplicationContextView context(UUID applicationId, UUID callerId, LocalDateTime now) {
        return applicationRepository.findContext(applicationId, callerId, now)
                .orElseThrow(() -> new BusinessException(ErrorCode.MERCENARY_002));
    }

    /** Trạng thái hiệu lực của đơn khác trạng thái kỳ vọng → MERCENARY_012. */
    private static void requireStatus(String effectiveStatus, String expected) {
        if (!expected.equals(effectiveStatus)) {
            throw new BusinessException(ErrorCode.MERCENARY_012);
        }
    }

    /** Frame APPLICATION_UPDATED tới cuộc trò chuyện của đơn, đẩy sau commit. */
    private void pushUpdate(ApplicationContextView ctx, MercenaryApplication.Status status,
                            MercenaryRecruitment.Status recruitmentStatus, int quantityApproved) {
        if (ctx.getConversationId() != null) {
            realtimeGateway.publishAfterCommit(ctx.getConversationId(), new RealtimeFrame.ApplicationUpdated(
                    ctx.getApplicationId(), status, ctx.getRecruitmentId(), recruitmentStatus, quantityApproved));
        }
    }
}
