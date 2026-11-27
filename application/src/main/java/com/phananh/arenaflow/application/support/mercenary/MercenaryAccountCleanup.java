package com.phananh.arenaflow.application.support.mercenary;

import com.phananh.arenaflow.application.event.mercenary.MercenaryApplicationWithdrawnEvent;
import com.phananh.arenaflow.application.support.ConcurrencyConflictException;
import com.phananh.arenaflow.domain.entity.mercenary.MercenaryApplication;
import com.phananh.arenaflow.domain.projection.ApprovedCountView;
import com.phananh.arenaflow.domain.projection.WithdrawnApplicationView;
import com.phananh.arenaflow.domain.repository.MercenaryApplicationRepository;
import com.phananh.arenaflow.domain.repository.MercenaryRecruitmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Xoá tài khoản ứng viên: rút mọi đơn PENDING/APPROVED của trận đã xác nhận chưa bắt đầu, không trừ điểm, tin FILLED về
 * OPEN, báo đội trưởng. Đơn APPROVED của trận đã huỷ / đã bắt đầu giữ nguyên.
 */
@Component
@RequiredArgsConstructor
public class MercenaryAccountCleanup {

    private final MercenaryRecruitmentRepository recruitmentRepository;
    private final MercenaryApplicationRepository applicationRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Chạy trong lần thử của xoá tài khoản (2 truy vấn). Khoá tin (bớt số đã duyệt) trước rồi mới tới đơn — cùng thứ tự
     * với duyệt / rút lui. Số đơn APPROVED vừa rút theo tin khác số đã bớt (đơn vừa được duyệt / rút giữa hai câu) →
     * {@link ConcurrencyConflictException} để thử lại cả lần xoá.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void withdrawForDeletedApplicant(UUID userId, LocalDateTime now) {
        Map<UUID, Long> decremented = recruitmentRepository.decrementApprovedOfApplicant(userId, now).stream()
                .collect(Collectors.toMap(ApprovedCountView::getRecruitmentId, ApprovedCountView::getN));
        List<WithdrawnApplicationView> withdrawn = applicationRepository.withdrawActiveOfApplicant(userId, now);
        Map<UUID, Long> approvedWithdrawn = withdrawn.stream().filter(view -> Boolean.TRUE.equals(view.getWasApproved()))
                .collect(Collectors.groupingBy(WithdrawnApplicationView::getRecruitmentId, Collectors.counting()));
        if (!decremented.equals(approvedWithdrawn)) {
            throw new ConcurrencyConflictException();
        }
        for (WithdrawnApplicationView view : withdrawn) {
            MercenaryApplication.Status previous = Boolean.TRUE.equals(view.getWasApproved())
                    ? MercenaryApplication.Status.APPROVED : MercenaryApplication.Status.PENDING;
            eventPublisher.publishEvent(new MercenaryApplicationWithdrawnEvent(view.getId(), view.getRecruitmentId(),
                    view.getMatchId(), view.getExternalMatchId(), view.getTeamId(), userId, previous,
                    MercenaryApplicationWithdrawnEvent.Cause.ACCOUNT_DELETED, now));
        }
    }
}
