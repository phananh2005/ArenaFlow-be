package com.phananh.arenaflow.application.service;

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

/**
 * Chuyển một domain event thành thông báo. Mỗi method chạy trong transaction mới (sau commit của transaction phát
 * event): tối đa một câu đọc người nhận + một câu ghi gom lô, bất kể số người nhận. Người nhận là đội trưởng tại lúc
 * tạo thông báo; dữ liệu đã đổi tiếp sau sự kiện được xử lý theo trạng thái hiện tại.
 */
public interface NotificationDispatcher {

    void userRegistered(UserRegisteredEvent event);

    void teamInvitationCreated(TeamInvitationCreatedEvent event);

    void teamInvitationResponded(TeamInvitationRespondedEvent event);

    void captainTransferred(CaptainTransferredEvent event);

    void teamStatusChangedByAdmin(TeamStatusChangedByAdminEvent event);

    void venueAdminAlert(VenueAdminAlertEvent event);

    void matchRequestSent(MatchRequestSentEvent event);

    void matchRequestRejected(MatchRequestRejectedEvent event);

    void matchRequestWithdrawn(MatchRequestWithdrawnEvent event);

    void matchConfirmed(MatchConfirmedEvent event);

    void pairingCreated(PairingCreatedEvent event);

    void matchCancelled(MatchCancelledEvent event);

    void matchExpired(MatchExpiredEvent event);

    void matchArchived(MatchArchivedEvent event);

    void matchRated(MatchRatedEvent event);

    void teamReported(TeamReportedEvent event);

    void reportDisputed(ReportDisputedEvent event);

    void disputeResolved(DisputeResolvedEvent event);

    void teamVerificationChanged(TeamVerificationChangedEvent event);

    void userVerificationChanged(UserVerificationChangedEvent event);

    void mercenaryApplicationSubmitted(MercenaryApplicationSubmittedEvent event);

    void mercenaryApplicationApproved(MercenaryApplicationApprovedEvent event);

    void mercenaryApplicationRejected(MercenaryApplicationRejectedEvent event);

    void mercenaryApplicationWithdrawn(MercenaryApplicationWithdrawnEvent event);

    void mercenaryRecruitmentClosed(MercenaryRecruitmentClosedEvent event);

    void mercenaryGameCancelled(MercenaryGameCancelledEvent event);

    void mercenaryRatingCreated(MercenaryRatingCreatedEvent event);

    void mercenaryReportCreated(MercenaryReportCreatedEvent event);
}
