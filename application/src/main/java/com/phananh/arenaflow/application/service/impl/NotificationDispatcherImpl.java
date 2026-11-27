package com.phananh.arenaflow.application.service.impl;

import com.phananh.arenaflow.application.event.match.MatchArchivedEvent;
import com.phananh.arenaflow.application.event.match.MatchCancelledEvent;
import com.phananh.arenaflow.application.event.match.MatchConfirmedEvent;
import com.phananh.arenaflow.application.event.match.MatchExpiredEvent;
import com.phananh.arenaflow.application.event.match.MatchRequestRejectedEvent;
import com.phananh.arenaflow.application.event.match.MatchRequestSentEvent;
import com.phananh.arenaflow.application.event.match.MatchRequestWithdrawnEvent;
import com.phananh.arenaflow.application.event.match.PairingCreatedEvent;
import com.phananh.arenaflow.application.event.mercenary.MercenaryApplicationApprovedEvent;
import com.phananh.arenaflow.application.event.mercenary.MercenaryApplicationRejectedEvent;
import com.phananh.arenaflow.application.event.mercenary.MercenaryApplicationSubmittedEvent;
import com.phananh.arenaflow.application.event.mercenary.MercenaryApplicationWithdrawnEvent;
import com.phananh.arenaflow.application.event.mercenary.MercenaryGameCancelledEvent;
import com.phananh.arenaflow.application.event.mercenary.MercenaryRatingCreatedEvent;
import com.phananh.arenaflow.application.event.mercenary.MercenaryRecruitmentClosedEvent;
import com.phananh.arenaflow.application.event.mercenary.MercenaryReportCreatedEvent;
import com.phananh.arenaflow.application.event.reputation.DisputeResolvedEvent;
import com.phananh.arenaflow.application.event.reputation.MatchRatedEvent;
import com.phananh.arenaflow.application.event.reputation.ReportDisputedEvent;
import com.phananh.arenaflow.application.event.reputation.TeamReportedEvent;
import com.phananh.arenaflow.application.event.reputation.TeamVerificationChangedEvent;
import com.phananh.arenaflow.application.event.reputation.UserVerificationChangedEvent;
import com.phananh.arenaflow.application.event.team.CaptainTransferredEvent;
import com.phananh.arenaflow.application.event.team.TeamInvitationCreatedEvent;
import com.phananh.arenaflow.application.event.team.TeamInvitationRespondedEvent;
import com.phananh.arenaflow.application.event.team.TeamStatusChangedByAdminEvent;
import com.phananh.arenaflow.application.event.user.UserRegisteredEvent;
import com.phananh.arenaflow.application.event.venue.VenueAdminAlertEvent;
import com.phananh.arenaflow.application.event.venue.VenueAlertType;
import com.phananh.arenaflow.application.service.NotificationDispatcher;
import com.phananh.arenaflow.application.support.mercenary.MercenarySide;
import com.phananh.arenaflow.application.support.notification.NotificationDraft;
import com.phananh.arenaflow.application.support.notification.NotificationTexts;
import com.phananh.arenaflow.application.support.notification.NotificationTexts.Text;
import com.phananh.arenaflow.application.support.notification.NotificationWriter;
import com.phananh.arenaflow.domain.entity.chat.Notification.ReferenceType;
import com.phananh.arenaflow.domain.entity.chat.Notification.Type;
import com.phananh.arenaflow.domain.entity.match.Match;
import com.phananh.arenaflow.domain.entity.mercenary.MercenaryApplication;
import com.phananh.arenaflow.domain.entity.team.Team;
import com.phananh.arenaflow.domain.projection.ApplicationAudienceRowView;
import com.phananh.arenaflow.domain.projection.GameAudienceRowView;
import com.phananh.arenaflow.domain.projection.MatchAudienceRowView;
import com.phananh.arenaflow.domain.projection.ReportAudienceRowView;
import com.phananh.arenaflow.domain.projection.TeamAudienceRowView;
import com.phananh.arenaflow.domain.repository.NotificationAudienceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Mỗi method: (Q1) một câu audience → bản nháp qua {@link NotificationTexts} → (Q2) một câu ghi gom lô qua
 * {@link NotificationWriter} (bỏ khi không có bản nháp). Dòng audience không còn đội trưởng (đội giải tán, đội trưởng
 * xoá tài khoản) cho người nhận null và bị writer bỏ. Không phát event.
 */
@Service
@RequiredArgsConstructor
public class NotificationDispatcherImpl implements NotificationDispatcher {

    private static final String LISTED = "LISTED";
    private static final String REQUEST = "REQUEST";
    private static final String PAIRING = "PAIRING";
    private static final String SUPERSEDED = "SUPERSEDED";
    private static final String EXPIRED = "EXPIRED";
    private static final UUID[] NO_IDS = new UUID[0];

    private final NotificationAudienceRepository audienceRepository;
    private final NotificationWriter notificationWriter;
    private final NotificationTexts texts;
    private final Clock clock;

    // ---------- tài khoản / đội ----------

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void userRegistered(UserRegisteredEvent event) {
        Text text = texts.welcome();
        write(List.of(draft(event.userId(), Type.WELCOME, text, ReferenceType.USER, event.userId())));
    }

    /** Lời mời đã được trả lời / huỷ trước khi listener chạy → không báo. */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void teamInvitationCreated(TeamInvitationCreatedEvent event) {
        audienceRepository.findInvitationAudience(event.invitationId())
                .filter(view -> "PENDING".equals(view.getInvitationStatus()))
                .ifPresent(view -> write(List.of(draft(view.getInviteeUserId(), Type.TEAM_INVITATION,
                        texts.teamInvitation(view.getTeamName()), ReferenceType.TEAM_INVITATION, event.invitationId()))));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void teamInvitationResponded(TeamInvitationRespondedEvent event) {
        team(event.teamId()).ifPresent(team -> write(List.of(event.accepted()
                ? draft(team.getCaptainUserId(), Type.TEAM_INVITATION_ACCEPTED, texts.teamInvitationAccepted(team.getTeamName()),
                ReferenceType.TEAM, event.teamId())
                : draft(team.getCaptainUserId(), Type.TEAM_INVITATION_REJECTED, texts.teamInvitationRejected(team.getTeamName()),
                ReferenceType.TEAM, event.teamId()))));
    }

    /** Chỉ đội trưởng mới (lấy từ payload) nhận; đội trưởng cũ — người thao tác — không nhận. */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void captainTransferred(CaptainTransferredEvent event) {
        team(event.teamId()).ifPresent(team -> write(List.of(draft(event.newCaptainUserId(), Type.CAPTAIN_TRANSFERRED,
                texts.captainTransferred(team.getTeamName()), ReferenceType.TEAM, event.teamId()))));
    }

    /** Không đưa lý do tạm ngưng (văn bản tự do của Admin) vào nội dung. */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void teamStatusChangedByAdmin(TeamStatusChangedByAdminEvent event) {
        team(event.teamId()).ifPresent(team -> {
            if (event.status() == Team.TeamStatus.INACTIVE) {
                write(List.of(draft(team.getCaptainUserId(), Type.TEAM_MARKED_INACTIVE,
                        texts.teamMarkedInactive(team.getTeamName()), ReferenceType.TEAM, event.teamId())));
            } else if (event.status() == Team.TeamStatus.ACTIVE) {
                write(List.of(draft(team.getCaptainUserId(), Type.TEAM_REACTIVATED,
                        texts.teamReactivated(team.getTeamName()), ReferenceType.TEAM, event.teamId())));
            }
        });
    }

    /** Mỗi Admin đang hoạt động một dòng; tên sân lấy từ payload. */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void venueAdminAlert(VenueAdminAlertEvent event) {
        Text text = event.alertType() == VenueAlertType.CLOSURE_REPORTED
                ? texts.venueClosureReported(event.venueName()) : texts.venueStale(event.venueName());
        write(audienceRepository.findActiveAdminIds().stream()
                .map(admin -> draft(admin, Type.VENUE_ADMIN_ALERT, text, ReferenceType.VENUE, event.venueId()))
                .toList());
    }

    // ---------- kèo ----------

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void matchRequestSent(MatchRequestSentEvent event) {
        List<MatchAudienceRowView> rows = matchAudience(event.matchId(), null, null, event.authorTeamId(),
                event.requesterTeamId());
        listed(rows, event.authorTeamId()).ifPresent(author -> listed(rows, event.requesterTeamId())
                .ifPresent(requester -> write(List.of(matchDraft(author.getCaptainUserId(), Type.MATCH_REQUEST_RECEIVED,
                        texts.matchRequestReceived(requester.getTeamName(), author.getMatchStartTime(),
                                author.getVenueName()), event.matchId())))));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void matchRequestRejected(MatchRequestRejectedEvent event) {
        List<MatchAudienceRowView> rows = matchAudience(event.matchId(), null, null, event.requesterTeamId());
        listed(rows, event.requesterTeamId()).ifPresent(requester -> write(List.of(matchDraft(
                requester.getCaptainUserId(), Type.REQUEST_REJECTED, texts.requestRejected(requester.getAuthorTeamName(),
                        requester.getMatchStartTime(), requester.getVenueName()), event.matchId()))));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void matchRequestWithdrawn(MatchRequestWithdrawnEvent event) {
        List<MatchAudienceRowView> rows = matchAudience(event.matchId(), null, null, event.authorTeamId(),
                event.requesterTeamId());
        listed(rows, event.authorTeamId()).ifPresent(author -> listed(rows, event.requesterTeamId())
                .ifPresent(requester -> write(List.of(matchDraft(author.getCaptainUserId(), Type.REQUEST_WITHDRAWN,
                        texts.requestWithdrawn(requester.getTeamName(), author.getMatchStartTime(), author.getVenueName()),
                        event.matchId())))));
    }

    /**
     * Luồng A: đội được chấp nhận + mỗi đội có yêu cầu bị thay thế (đội tạo kèo — người bấm chấp nhận — không nhận).
     * Luồng B: đội trưởng hai đội + mỗi đội có thương lượng bị thay thế.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void matchConfirmed(MatchConfirmedEvent event) {
        boolean flowA = event.acceptedRequestId() != null;
        List<MatchAudienceRowView> rows = matchAudience(event.matchId(), flowA ? SUPERSEDED : null,
                flowA ? null : SUPERSEDED, event.authorTeamId(), event.opponentTeamId());
        if (rows.isEmpty()) {
            return;
        }
        MatchAudienceRowView any = rows.getFirst();
        List<NotificationDraft> drafts = new ArrayList<>();
        Optional<MatchAudienceRowView> opponent = listed(rows, event.opponentTeamId());
        if (flowA) {
            opponent.ifPresent(row -> drafts.add(matchDraft(row.getCaptainUserId(), Type.REQUEST_ACCEPTED,
                    texts.requestAccepted(row.getAuthorTeamName(), row.getMatchStartTime(), row.getVenueName()),
                    event.matchId())));
            bySource(rows, REQUEST).forEach(row -> drafts.add(matchDraft(row.getCaptainUserId(), Type.REQUEST_SUPERSEDED,
                    texts.requestSuperseded(row.getAuthorTeamName(), row.getMatchStartTime(), row.getVenueName()),
                    event.matchId())));
        } else {
            String opponentName = opponent.map(MatchAudienceRowView::getTeamName).orElse("");
            Text confirmed = texts.matchConfirmed(any.getAuthorTeamName(), opponentName, any.getMatchStartTime(),
                    any.getVenueName());
            listed(rows, event.authorTeamId()).ifPresent(row -> drafts.add(matchDraft(row.getCaptainUserId(),
                    Type.MATCH_CONFIRMED, confirmed, event.matchId())));
            opponent.ifPresent(row -> drafts.add(matchDraft(row.getCaptainUserId(), Type.MATCH_CONFIRMED, confirmed,
                    event.matchId())));
            bySource(rows, PAIRING).forEach(row -> drafts.add(matchDraft(row.getCaptainUserId(), Type.PAIRING_SUPERSEDED,
                    texts.pairingSuperseded(row.getAuthorTeamName()), event.matchId())));
        }
        write(drafts);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void pairingCreated(PairingCreatedEvent event) {
        List<MatchAudienceRowView> rows = matchAudience(event.matchId(), null, null, event.authorTeamId(),
                event.interestedTeamId());
        listed(rows, event.authorTeamId()).ifPresent(author -> listed(rows, event.interestedTeamId())
                .ifPresent(interested -> write(List.of(draft(author.getCaptainUserId(), Type.PAIRING_NEW,
                        texts.pairingNew(interested.getTeamName()), ReferenceType.PAIRING, event.pairingId())))));
    }

    /**
     * Kèo đã ghép bị huỷ (kể cả đội đã ghép rút yêu cầu) → đội còn lại. Kèo OPEN bị huỷ → các đội đang thương lượng
     * (kèo luồng A OPEN không có yêu cầu chờ khi huỷ nên không ai nhận).
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void matchCancelled(MatchCancelledEvent event) {
        if (event.previousStatus() == Match.Status.CONFIRMED) {
            List<MatchAudienceRowView> rows = matchAudience(event.matchId(), null, null, event.cancelledByTeamId(),
                    event.otherTeamId());
            listed(rows, event.otherTeamId()).ifPresent(other -> listed(rows, event.cancelledByTeamId())
                    .ifPresent(cancelling -> write(List.of(matchDraft(other.getCaptainUserId(), Type.MATCH_CANCELLED,
                            texts.matchCancelledConfirmed(cancelling.getTeamName(), other.getMatchStartTime(),
                                    other.getVenueName()), event.matchId())))));
        } else if (event.previousStatus() == Match.Status.OPEN) {
            List<MatchAudienceRowView> rows = matchAudience(event.matchId(), null, SUPERSEDED, event.cancelledByTeamId());
            write(bySource(rows, PAIRING).stream().map(row -> matchDraft(row.getCaptainUserId(), Type.MATCH_CANCELLED,
                    texts.matchCancelledNegotiating(row.getAuthorTeamName()), event.matchId())).toList());
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void matchExpired(MatchExpiredEvent event) {
        List<MatchAudienceRowView> rows = matchAudience(event.matchId(), EXPIRED, EXPIRED, event.authorTeamId());
        List<NotificationDraft> drafts = new ArrayList<>();
        listed(rows, event.authorTeamId()).ifPresent(author -> drafts.add(matchDraft(author.getCaptainUserId(),
                Type.MATCH_EXPIRED, texts.matchExpired(author.getMatchStartTime(), author.getVenueName()), event.matchId())));
        bySource(rows, REQUEST).forEach(row -> drafts.add(matchDraft(row.getCaptainUserId(), Type.REQUEST_EXPIRED,
                texts.requestExpired(row.getAuthorTeamName(), row.getMatchStartTime(), row.getVenueName()),
                event.matchId())));
        bySource(rows, PAIRING).forEach(row -> drafts.add(matchDraft(row.getCaptainUserId(), Type.PAIRING_EXPIRED,
                texts.pairingExpired(row.getAuthorTeamName()), event.matchId())));
        write(drafts);
    }

    /**
     * Nhắc nhập tỷ số / đánh giá: đội trưởng hai đội + mercenary đã duyệt của kèo. Trạng thái COMPLETED cũng tính vì
     * một đội có thể đã đánh giá trước khi listener chạy.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void matchArchived(MatchArchivedEvent event) {
        List<GameAudienceRowView> rows = audienceRepository.findGameAudience(new UUID[]{event.matchId()},
                new String[]{Match.Status.ARCHIVED.name(), Match.Status.COMPLETED.name()}, NO_IDS);
        write(rows.stream().map(row -> "CAPTAIN".equals(row.getKind())
                ? matchDraft(row.getRecipientUserId(), Type.MATCH_COMPLETED,
                texts.matchCompleted(row.getOpponentTeamName(), row.getStartTime()), event.matchId())
                : draft(row.getRecipientUserId(), Type.MERCENARY_RATING_REMINDER,
                texts.mercenaryRatingReminder(row.getTeamName(), row.getStartTime(), row.getPosition()),
                ReferenceType.MERCENARY_APPLICATION, row.getApplicationId())).toList());
    }

    /** Chỉ báo đội được đánh giá khi đội đó đã đánh giá trận trước — không lộ việc chờ đánh giá. */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void matchRated(MatchRatedEvent event) {
        List<MatchAudienceRowView> rows = matchAudience(event.matchId(), null, null, event.raterTeamId(),
                event.rateeTeamId());
        listed(rows, event.rateeTeamId()).filter(ratee -> Boolean.TRUE.equals(ratee.getHasRated()))
                .ifPresent(ratee -> listed(rows, event.raterTeamId()).ifPresent(rater -> write(List.of(matchDraft(
                        ratee.getCaptainUserId(), Type.RATING_RECEIVED,
                        texts.ratingReceivedFromTeam(rater.getTeamName(), ratee.getMatchStartTime()), event.matchId())))));
    }

    // ---------- report / tranh chấp / huy hiệu ----------

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void teamReported(TeamReportedEvent event) {
        reportReceived(event.reportId());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void mercenaryReportCreated(MercenaryReportCreatedEvent event) {
        reportReceived(event.reportId());
    }

    /** Bên đã report nhận REPORT_DISPUTED; mỗi Admin nhận DISPUTE_CREATED trỏ tới tranh chấp (đội hoặc mercenary). */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reportDisputed(ReportDisputedEvent event) {
        List<NotificationDraft> drafts = new ArrayList<>();
        for (ReportAudienceRowView row : audienceRepository.findReportAudience(event.reportId(), true)) {
            if ("REPORTER".equals(row.getAudience())) {
                Text text = "TEAM".equals(row.getReportedType()) ? texts.reportDisputedByTeam(row.getReportedTeamName())
                        : texts.reportDisputedByMercenary(row.getPosition());
                drafts.add(draft(row.getRecipientUserId(), Type.REPORT_DISPUTED, text, ReferenceType.REPORT,
                        event.reportId()));
            } else if ("ADMIN".equals(row.getAudience())) {
                ReferenceType reference = row.getApplicationId() == null ? ReferenceType.DISPUTE
                        : ReferenceType.MERCENARY_DISPUTE;
                drafts.add(draft(row.getRecipientUserId(), Type.DISPUTE_CREATED,
                        texts.disputeCreated(row.getGameStartTime()), reference, event.disputeId()));
            }
        }
        write(drafts);
    }

    /** Hai bên nhận; biến thể theo quyết định (đọc tại lúc tạo thông báo) × bên. */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void disputeResolved(DisputeResolvedEvent event) {
        List<NotificationDraft> drafts = new ArrayList<>();
        for (ReportAudienceRowView row : audienceRepository.findReportAudience(event.reportId(), false)) {
            String decision = row.getDecision() != null ? row.getDecision() : event.decision().name();
            Text text;
            if ("CONFIRMED".equals(decision)) {
                text = texts.disputeConfirmed(row.getGameStartTime());
            } else if ("REPORTED".equals(row.getAudience())) {
                text = texts.disputeRejectedForReported(row.getGameStartTime());
            } else {
                text = texts.disputeRejectedForReporter(row.getGameStartTime());
            }
            drafts.add(draft(row.getRecipientUserId(), Type.DISPUTE_RESOLVED, text, ReferenceType.REPORT, event.reportId()));
        }
        write(drafts);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void teamVerificationChanged(TeamVerificationChangedEvent event) {
        team(event.teamId()).ifPresent(team -> write(List.of(event.verified()
                ? draft(team.getCaptainUserId(), Type.VERIFICATION_BADGE_GRANTED,
                texts.verificationGrantedTeam(team.getTeamName()), ReferenceType.TEAM, event.teamId())
                : draft(team.getCaptainUserId(), Type.VERIFICATION_BADGE_REVOKED,
                texts.verificationRevokedTeam(team.getTeamName()), ReferenceType.TEAM, event.teamId()))));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void userVerificationChanged(UserVerificationChangedEvent event) {
        write(List.of(event.verified()
                ? draft(event.userId(), Type.VERIFICATION_BADGE_GRANTED, texts.verificationGrantedUser(),
                ReferenceType.USER, event.userId())
                : draft(event.userId(), Type.VERIFICATION_BADGE_REVOKED, texts.verificationRevokedUser(),
                ReferenceType.USER, event.userId())));
    }

    // ---------- mercenary ----------

    /** Đơn không còn chờ (đã rút / bị từ chối) trước khi listener chạy → không báo. */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void mercenaryApplicationSubmitted(MercenaryApplicationSubmittedEvent event) {
        applicationDrafts(new UUID[]{event.applicationId()}, row -> "PENDING".equals(row.getApplicationStatus())
                ? applicationDraft(row.getCaptainUserId(), Type.MERCENARY_APPLICATION,
                texts.mercenaryApplication(row.getPosition(), row.getGameStartTime(), row.getTeamName()), row)
                : null);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void mercenaryApplicationApproved(MercenaryApplicationApprovedEvent event) {
        applicationDrafts(new UUID[]{event.applicationId()}, row -> applicationDraft(row.getApplicantId(),
                Type.MERCENARY_APPLICATION_APPROVED, texts.mercenaryApproved(row.getTeamName(), row.getPosition(),
                        row.getGameStartTime(), row.getVenueName()), row));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void mercenaryApplicationRejected(MercenaryApplicationRejectedEvent event) {
        boolean byCaptain = event.cause() == MercenaryApplicationRejectedEvent.Cause.CAPTAIN;
        applicationDrafts(new UUID[]{event.applicationId()}, row -> applicationDraft(row.getApplicantId(),
                Type.MERCENARY_APPLICATION_REJECTED, byCaptain
                        ? texts.mercenaryRejectedByCaptain(row.getTeamName(), row.getPosition(), row.getGameStartTime())
                        : texts.mercenaryRejectedClosed(row.getPosition(), row.getTeamName(), row.getGameStartTime()), row));
    }

    /** Ứng viên tự rút đơn đang chờ → không báo, không đọc DB. Rút sau khi duyệt / xoá tài khoản → đội trưởng. */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void mercenaryApplicationWithdrawn(MercenaryApplicationWithdrawnEvent event) {
        boolean accountDeleted = event.cause() == MercenaryApplicationWithdrawnEvent.Cause.ACCOUNT_DELETED;
        if (!accountDeleted && event.previousStatus() != MercenaryApplication.Status.APPROVED) {
            return;
        }
        applicationDrafts(new UUID[]{event.applicationId()}, row -> applicationDraft(row.getCaptainUserId(),
                Type.MERCENARY_WITHDRAWN, accountDeleted
                        ? texts.mercenaryWithdrawnAccountDeleted(row.getPosition(), row.getGameStartTime())
                        : texts.mercenaryWithdrawnByApplicant(row.getPosition(), row.getGameStartTime()), row));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void mercenaryRecruitmentClosed(MercenaryRecruitmentClosedEvent event) {
        if (event.rejectedApplicationIds() == null || event.rejectedApplicationIds().isEmpty()) {
            return;
        }
        applicationDrafts(event.rejectedApplicationIds().toArray(UUID[]::new), row -> applicationDraft(
                row.getApplicantId(), Type.MERCENARY_APPLICATION_REJECTED,
                texts.mercenaryRejectedClosed(row.getPosition(), row.getTeamName(), row.getGameStartTime()), row));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void mercenaryGameCancelled(MercenaryGameCancelledEvent event) {
        if (event.applicationIds() == null || event.applicationIds().isEmpty()) {
            return;
        }
        applicationDrafts(event.applicationIds().toArray(UUID[]::new), row -> applicationDraft(row.getApplicantId(),
                Type.MERCENARY_GAME_CANCELLED, texts.mercenaryGameCancelled(row.getGameStartTime(), row.getTeamName(),
                        row.getPosition()), row));
    }

    /** Bên được đánh giá chỉ nhận khi chính bên đó đã đánh giá bên kia trong cùng đơn. */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void mercenaryRatingCreated(MercenaryRatingCreatedEvent event) {
        boolean captainRated = event.direction() == MercenarySide.CAPTAIN;
        applicationDrafts(new UUID[]{event.applicationId()}, row -> {
            if (captainRated) {
                return Boolean.TRUE.equals(row.getRatedByApplicant())
                        ? applicationDraft(row.getApplicantId(), Type.RATING_RECEIVED,
                        texts.ratingReceivedByMercenaryFromTeam(row.getTeamName(), row.getGameStartTime()), row)
                        : null;
            }
            return Boolean.TRUE.equals(row.getRatedByTeam())
                    ? applicationDraft(row.getCaptainUserId(), Type.RATING_RECEIVED,
                    texts.ratingReceivedByTeamFromMercenary(row.getPosition(), row.getGameStartTime()), row)
                    : null;
        });
    }

    // ---------- helpers ----------

    /** Bên bị report nhận; người report là đội → tên đội, là mercenary → vị trí. */
    private void reportReceived(UUID reportId) {
        write(audienceRepository.findReportAudience(reportId, false).stream()
                .filter(row -> "REPORTED".equals(row.getAudience()))
                .map(row -> draft(row.getRecipientUserId(), Type.REPORT_RECEIVED,
                        "TEAM".equals(row.getReporterType())
                                ? texts.reportReceivedFromTeam(row.getReporterTeamName(),
                                "TEAM".equals(row.getReportedType()), row.getReportType())
                                : texts.reportReceivedFromMercenary(row.getPosition(), row.getReportType()),
                        ReferenceType.REPORT, reportId))
                .toList());
    }

    private void applicationDrafts(UUID[] applicationIds, Function<ApplicationAudienceRowView, NotificationDraft> mapper) {
        write(audienceRepository.findApplicationAudience(applicationIds).stream().map(mapper)
                .filter(draft -> draft != null).toList());
    }

    private List<MatchAudienceRowView> matchAudience(UUID matchId, String requestStatus, String pairingStatus,
                                                     UUID... teamIds) {
        return audienceRepository.findMatchAudience(matchId, teamIds, requestStatus, pairingStatus);
    }

    private static Optional<MatchAudienceRowView> listed(List<MatchAudienceRowView> rows, UUID teamId) {
        return rows.stream().filter(row -> LISTED.equals(row.getSource()) && row.getTeamId().equals(teamId)).findFirst();
    }

    private static List<MatchAudienceRowView> bySource(List<MatchAudienceRowView> rows, String source) {
        return rows.stream().filter(row -> source.equals(row.getSource())).toList();
    }

    private Optional<TeamAudienceRowView> team(UUID teamId) {
        return audienceRepository.findTeamAudience(new UUID[]{teamId}).stream().findFirst();
    }

    private static NotificationDraft draft(UUID recipient, Type type, Text text, ReferenceType referenceType,
                                           UUID referenceId) {
        return new NotificationDraft(recipient, type, text.title(), text.body(), referenceType, referenceId);
    }

    private static NotificationDraft matchDraft(UUID recipient, Type type, Text text, UUID matchId) {
        return draft(recipient, type, text, ReferenceType.MATCH, matchId);
    }

    private static NotificationDraft applicationDraft(UUID recipient, Type type, Text text, ApplicationAudienceRowView row) {
        return draft(recipient, type, text, ReferenceType.MERCENARY_APPLICATION, row.getApplicationId());
    }

    /** Bỏ câu ghi khi không còn bản nháp có người nhận. */
    private void write(List<NotificationDraft> drafts) {
        List<NotificationDraft> withRecipient = drafts.stream().filter(draft -> draft.recipientUserId() != null).toList();
        if (!withRecipient.isEmpty()) {
            notificationWriter.write(withRecipient, LocalDateTime.now(clock));
        }
    }
}
