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
import com.phananh.arenaflow.application.event.venue.VenueAlertType;
import com.phananh.arenaflow.application.service.impl.NotificationDispatcherImpl;
import com.phananh.arenaflow.application.support.mercenary.MercenarySide;
import com.phananh.arenaflow.application.support.notification.NotificationDraft;
import com.phananh.arenaflow.application.support.notification.NotificationTexts;
import com.phananh.arenaflow.application.support.notification.NotificationWriter;
import com.phananh.arenaflow.domain.entity.chat.Notification.ReferenceType;
import com.phananh.arenaflow.domain.entity.chat.Notification.Type;
import com.phananh.arenaflow.domain.entity.match.Match;
import com.phananh.arenaflow.domain.entity.mercenary.MercenaryApplication;
import com.phananh.arenaflow.domain.entity.rating.Report;
import com.phananh.arenaflow.domain.entity.team.Team;
import com.phananh.arenaflow.domain.projection.ApplicationAudienceRowView;
import com.phananh.arenaflow.domain.projection.GameAudienceRowView;
import com.phananh.arenaflow.domain.projection.InvitationAudienceView;
import com.phananh.arenaflow.domain.projection.MatchAudienceRowView;
import com.phananh.arenaflow.domain.projection.ReportAudienceRowView;
import com.phananh.arenaflow.domain.projection.TeamAudienceRowView;
import com.phananh.arenaflow.domain.repository.NotificationAudienceRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NotificationDispatcherImplTest {

    /** 10:00 giờ Việt Nam. */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 21, 10, 0);
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 22, 18, 0);
    private static final UUID MATCH = UUID.randomUUID();
    private static final UUID AUTHOR = UUID.randomUUID();
    private static final UUID OPPONENT = UUID.randomUUID();
    private static final UUID AUTHOR_CAPTAIN = UUID.randomUUID();
    private static final UUID OPPONENT_CAPTAIN = UUID.randomUUID();

    private final NotificationAudienceRepository audience = mock(NotificationAudienceRepository.class);
    private final NotificationWriter writer = mock(NotificationWriter.class);
    private final SpelAwareProxyProjectionFactory projections = new SpelAwareProxyProjectionFactory();
    private final NotificationDispatcher dispatcher = new NotificationDispatcherImpl(audience, writer,
            new NotificationTexts(), Clock.fixed(Instant.parse("2026-11-21T03:00:00Z"), ZoneId.systemDefault()));

    // ---------- tài khoản / đội ----------

    @Test
    void userRegisteredSendsWelcomeWithoutQuery() {
        UUID user = UUID.randomUUID();

        dispatcher.userRegistered(new UserRegisteredEvent(user, NOW));

        verifyNoInteractions(audience);
        assertThat(drafts()).extracting(NotificationDraft::recipientUserId, NotificationDraft::type,
                NotificationDraft::referenceType, NotificationDraft::referenceId)
                .containsExactly(tuple(user, Type.WELCOME, ReferenceType.USER, user));
    }

    @Test
    void invitationCreatedOnlyWhilePending() {
        UUID invitation = UUID.randomUUID();
        UUID invitee = UUID.randomUUID();
        when(audience.findInvitationAudience(invitation)).thenReturn(Optional.of(projection(InvitationAudienceView.class,
                Map.of("invitationStatus", "PENDING", "inviteeUserId", invitee, "teamName", "FC A"))));

        dispatcher.teamInvitationCreated(new TeamInvitationCreatedEvent(invitation, AUTHOR, invitee, NOW));

        assertThat(drafts()).extracting(NotificationDraft::recipientUserId, NotificationDraft::type,
                NotificationDraft::referenceType, NotificationDraft::referenceId)
                .containsExactly(tuple(invitee, Type.TEAM_INVITATION, ReferenceType.TEAM_INVITATION, invitation));
    }

    @Test
    void invitationNoLongerPendingIsSkipped() {
        UUID invitation = UUID.randomUUID();
        when(audience.findInvitationAudience(invitation)).thenReturn(Optional.of(projection(InvitationAudienceView.class,
                Map.of("invitationStatus", "ACTIVE", "inviteeUserId", UUID.randomUUID(), "teamName", "FC A"))));

        dispatcher.teamInvitationCreated(new TeamInvitationCreatedEvent(invitation, AUTHOR, UUID.randomUUID(), NOW));

        verify(writer, never()).write(any(), any());
    }

    @Test
    void invitationRespondedNotifiesCaptain() {
        stubTeam(AUTHOR, "FC A", AUTHOR_CAPTAIN);

        dispatcher.teamInvitationResponded(new TeamInvitationRespondedEvent(UUID.randomUUID(), AUTHOR, false, NOW));

        assertThat(drafts()).extracting(NotificationDraft::recipientUserId, NotificationDraft::type,
                NotificationDraft::referenceType)
                .containsExactly(tuple(AUTHOR_CAPTAIN, Type.TEAM_INVITATION_REJECTED, ReferenceType.TEAM));
    }

    @Test
    void captainTransferredNotifiesOnlyNewCaptain() {
        UUID newCaptain = UUID.randomUUID();
        stubTeam(AUTHOR, "FC A", newCaptain);

        dispatcher.captainTransferred(new CaptainTransferredEvent(AUTHOR, newCaptain, AUTHOR_CAPTAIN, NOW));

        assertThat(drafts()).extracting(NotificationDraft::recipientUserId, NotificationDraft::type)
                .containsExactly(tuple(newCaptain, Type.CAPTAIN_TRANSFERRED));
    }

    @Test
    void teamStatusChangedByAdmin() {
        stubTeam(AUTHOR, "FC A", AUTHOR_CAPTAIN);

        dispatcher.teamStatusChangedByAdmin(new TeamStatusChangedByAdminEvent(AUTHOR, Team.TeamStatus.INACTIVE, NOW));
        dispatcher.teamStatusChangedByAdmin(new TeamStatusChangedByAdminEvent(AUTHOR, Team.TeamStatus.ACTIVE, NOW));

        assertThat(allDrafts(2)).extracting(NotificationDraft::type)
                .containsExactly(Type.TEAM_MARKED_INACTIVE, Type.TEAM_REACTIVATED);
    }

    @Test
    void disbandedTeamWithoutCaptainProducesNothing() {
        stubTeam(AUTHOR, "FC A", null);

        dispatcher.teamInvitationResponded(new TeamInvitationRespondedEvent(UUID.randomUUID(), AUTHOR, true, NOW));

        verify(writer, never()).write(any(), any());
    }

    @Test
    void venueAdminAlertGoesToEveryAdmin() {
        UUID venue = UUID.randomUUID();
        UUID admin1 = UUID.randomUUID();
        UUID admin2 = UUID.randomUUID();
        when(audience.findActiveAdminIds()).thenReturn(List.of(admin1, admin2));

        dispatcher.venueAdminAlert(new VenueAdminAlertEvent(venue, "Sân X", VenueAlertType.CLOSURE_REPORTED,
                LocalDateTime.of(2026, 11, 21, 10, 0)));

        List<NotificationDraft> drafts = drafts();
        assertThat(drafts).extracting(NotificationDraft::recipientUserId).containsExactly(admin1, admin2);
        assertThat(drafts).allSatisfy(draft -> {
            assertThat(draft.type()).isEqualTo(Type.VENUE_ADMIN_ALERT);
            assertThat(draft.referenceType()).isEqualTo(ReferenceType.VENUE);
            assertThat(draft.body()).contains("Sân X").contains("đóng cửa");
        });
    }

    @Test
    void venueAdminAlertWithoutAdminDoesNotWrite() {
        when(audience.findActiveAdminIds()).thenReturn(List.of());

        dispatcher.venueAdminAlert(new VenueAdminAlertEvent(UUID.randomUUID(), "Sân X", VenueAlertType.STALE_180_DAYS,
                LocalDateTime.of(2026, 11, 21, 10, 0)));

        verify(writer, never()).write(any(), any());
    }

    // ---------- kèo ----------

    @Test
    void matchRequestSentNotifiesAuthorCaptain() {
        stubMatch(null, null, listed(AUTHOR, "FC A", AUTHOR_CAPTAIN), listed(OPPONENT, "FC B", OPPONENT_CAPTAIN));

        dispatcher.matchRequestSent(new MatchRequestSentEvent(UUID.randomUUID(), MATCH, OPPONENT, AUTHOR, NOW));

        NotificationDraft draft = drafts().getFirst();
        assertThat(draft.recipientUserId()).isEqualTo(AUTHOR_CAPTAIN);
        assertThat(draft.type()).isEqualTo(Type.MATCH_REQUEST_RECEIVED);
        assertThat(draft.referenceType()).isEqualTo(ReferenceType.MATCH);
        assertThat(draft.referenceId()).isEqualTo(MATCH);
        assertThat(draft.body()).isEqualTo("Đội FC B muốn ghép kèo 18:00 22/11 tại Sân X.");
    }

    @Test
    void matchRequestRejectedAndWithdrawn() {
        stubMatch(null, null, listed(OPPONENT, "FC B", OPPONENT_CAPTAIN));
        dispatcher.matchRequestRejected(new MatchRequestRejectedEvent(UUID.randomUUID(), MATCH, OPPONENT, NOW));
        assertThat(drafts()).extracting(NotificationDraft::recipientUserId, NotificationDraft::type)
                .containsExactly(tuple(OPPONENT_CAPTAIN, Type.REQUEST_REJECTED));
    }

    @Test
    void matchRequestWithdrawnNotifiesAuthor() {
        stubMatch(null, null, listed(AUTHOR, "FC A", AUTHOR_CAPTAIN), listed(OPPONENT, "FC B", OPPONENT_CAPTAIN));

        dispatcher.matchRequestWithdrawn(new MatchRequestWithdrawnEvent(UUID.randomUUID(), MATCH, OPPONENT, AUTHOR, NOW));

        assertThat(drafts()).extracting(NotificationDraft::recipientUserId, NotificationDraft::type)
                .containsExactly(tuple(AUTHOR_CAPTAIN, Type.REQUEST_WITHDRAWN));
    }

    @Test
    void flowAConfirmedNotifiesAcceptedAndSupersededButNotAuthor() {
        UUID loser = UUID.randomUUID();
        UUID loserCaptain = UUID.randomUUID();
        stubMatch("SUPERSEDED", null, listed(AUTHOR, "FC A", AUTHOR_CAPTAIN), listed(OPPONENT, "FC B", OPPONENT_CAPTAIN),
                row("REQUEST", loser, "FC C", loserCaptain, false));

        dispatcher.matchConfirmed(new MatchConfirmedEvent(MATCH, UUID.randomUUID(), null, AUTHOR, OPPONENT, NOW));

        assertThat(drafts()).extracting(NotificationDraft::recipientUserId, NotificationDraft::type)
                .containsExactly(tuple(OPPONENT_CAPTAIN, Type.REQUEST_ACCEPTED), tuple(loserCaptain, Type.REQUEST_SUPERSEDED));
    }

    @Test
    void flowBConfirmedNotifiesBothCaptainsAndSupersededPairings() {
        UUID loserCaptain = UUID.randomUUID();
        stubMatch(null, "SUPERSEDED", listed(AUTHOR, "FC A", AUTHOR_CAPTAIN), listed(OPPONENT, "FC B", OPPONENT_CAPTAIN),
                row("PAIRING", UUID.randomUUID(), "FC C", loserCaptain, false));

        dispatcher.matchConfirmed(new MatchConfirmedEvent(MATCH, null, UUID.randomUUID(), AUTHOR, OPPONENT, NOW));

        assertThat(drafts()).extracting(NotificationDraft::recipientUserId, NotificationDraft::type)
                .containsExactly(tuple(AUTHOR_CAPTAIN, Type.MATCH_CONFIRMED), tuple(OPPONENT_CAPTAIN, Type.MATCH_CONFIRMED),
                        tuple(loserCaptain, Type.PAIRING_SUPERSEDED));
    }

    @Test
    void pairingCreatedReferencesPairing() {
        UUID pairing = UUID.randomUUID();
        stubMatch(null, null, listed(AUTHOR, "FC A", AUTHOR_CAPTAIN), listed(OPPONENT, "FC B", OPPONENT_CAPTAIN));

        dispatcher.pairingCreated(new PairingCreatedEvent(pairing, MATCH, OPPONENT, AUTHOR, NOW));

        assertThat(drafts()).extracting(NotificationDraft::recipientUserId, NotificationDraft::type,
                NotificationDraft::referenceType, NotificationDraft::referenceId)
                .containsExactly(tuple(AUTHOR_CAPTAIN, Type.PAIRING_NEW, ReferenceType.PAIRING, pairing));
    }

    @Test
    void cancelledConfirmedMatchNotifiesOtherTeam() {
        stubMatch(null, null, listed(AUTHOR, "FC A", AUTHOR_CAPTAIN), listed(OPPONENT, "FC B", OPPONENT_CAPTAIN));

        dispatcher.matchCancelled(new MatchCancelledEvent(MATCH, OPPONENT, AUTHOR, Match.Status.CONFIRMED,
                MatchCancelledEvent.Cause.CANCEL, null, NOW));

        NotificationDraft draft = drafts().getFirst();
        assertThat(draft.recipientUserId()).isEqualTo(AUTHOR_CAPTAIN);
        assertThat(draft.type()).isEqualTo(Type.MATCH_CANCELLED);
        assertThat(draft.body()).startsWith("Đội FC B đã huỷ kèo");
    }

    @Test
    void cancelledOpenMatchNotifiesNegotiatingTeamsOnly() {
        UUID negotiatingCaptain = UUID.randomUUID();
        stubMatch(null, "SUPERSEDED", listed(AUTHOR, "FC A", AUTHOR_CAPTAIN),
                row("PAIRING", UUID.randomUUID(), "FC C", negotiatingCaptain, false));

        dispatcher.matchCancelled(new MatchCancelledEvent(MATCH, AUTHOR, null, Match.Status.OPEN,
                MatchCancelledEvent.Cause.CANCEL, null, NOW));

        assertThat(drafts()).extracting(NotificationDraft::recipientUserId, NotificationDraft::type)
                .containsExactly(tuple(negotiatingCaptain, Type.MATCH_CANCELLED));
    }

    @Test
    void expiredMatchNotifiesAuthorAndExpiredRequestsAndPairings() {
        UUID requestCaptain = UUID.randomUUID();
        UUID pairingCaptain = UUID.randomUUID();
        stubMatch("EXPIRED", "EXPIRED", listed(AUTHOR, "FC A", AUTHOR_CAPTAIN),
                row("REQUEST", UUID.randomUUID(), "FC C", requestCaptain, false),
                row("PAIRING", UUID.randomUUID(), "FC D", pairingCaptain, false));

        dispatcher.matchExpired(new MatchExpiredEvent(MATCH, AUTHOR, NOW));

        assertThat(drafts()).extracting(NotificationDraft::recipientUserId, NotificationDraft::type)
                .containsExactly(tuple(AUTHOR_CAPTAIN, Type.MATCH_EXPIRED), tuple(requestCaptain, Type.REQUEST_EXPIRED),
                        tuple(pairingCaptain, Type.PAIRING_EXPIRED));
    }

    @Test
    void archivedMatchRemindsCaptainsAndApprovedMercenaries() {
        UUID mercenary = UUID.randomUUID();
        UUID application = UUID.randomUUID();
        when(audience.findGameAudience(any(), any(), any())).thenReturn(List.of(
                game("CAPTAIN", AUTHOR_CAPTAIN, null, "FC A", "FC B", null),
                game("CAPTAIN", OPPONENT_CAPTAIN, null, "FC B", "FC A", null),
                game("MERCENARY", mercenary, application, "FC A", null, "Thủ môn")));

        dispatcher.matchArchived(new MatchArchivedEvent(MATCH, AUTHOR, OPPONENT, START, NOW));

        ArgumentCaptor<String[]> statuses = ArgumentCaptor.forClass(String[].class);
        verify(audience).findGameAudience(eq(new UUID[]{MATCH}), statuses.capture(), eq(new UUID[0]));
        assertThat(statuses.getValue()).containsExactly("ARCHIVED", "COMPLETED");
        assertThat(drafts()).extracting(NotificationDraft::recipientUserId, NotificationDraft::type,
                NotificationDraft::referenceType, NotificationDraft::referenceId)
                .containsExactly(tuple(AUTHOR_CAPTAIN, Type.MATCH_COMPLETED, ReferenceType.MATCH, MATCH),
                        tuple(OPPONENT_CAPTAIN, Type.MATCH_COMPLETED, ReferenceType.MATCH, MATCH),
                        tuple(mercenary, Type.MERCENARY_RATING_REMINDER, ReferenceType.MERCENARY_APPLICATION, application));
    }

    @Test
    void matchRatedNotifiesRateeOnlyIfItAlreadyRated() {
        stubMatch(null, null, listed(AUTHOR, "FC A", AUTHOR_CAPTAIN), row("LISTED", OPPONENT, "FC B", OPPONENT_CAPTAIN, true));
        dispatcher.matchRated(new MatchRatedEvent(UUID.randomUUID(), MATCH, AUTHOR, OPPONENT, false, true, NOW));
        assertThat(drafts()).extracting(NotificationDraft::recipientUserId, NotificationDraft::type)
                .containsExactly(tuple(OPPONENT_CAPTAIN, Type.RATING_RECEIVED));
    }

    @Test
    void matchRatedSkipsRateeThatHasNotRated() {
        stubMatch(null, null, listed(AUTHOR, "FC A", AUTHOR_CAPTAIN), row("LISTED", OPPONENT, "FC B", OPPONENT_CAPTAIN, false));

        dispatcher.matchRated(new MatchRatedEvent(UUID.randomUUID(), MATCH, AUTHOR, OPPONENT, false, true, NOW));

        verify(writer, never()).write(any(), any());
    }

    // ---------- report / tranh chấp / huy hiệu ----------

    @Test
    void teamReportedNotifiesReportedWithDeadlineForNoShow() {
        UUID report = UUID.randomUUID();
        UUID reportedCaptain = UUID.randomUUID();
        when(audience.findReportAudience(report, false)).thenReturn(List.of(
                reportRow("REPORTER", AUTHOR_CAPTAIN, report, "NOSHOW", "TEAM", "TEAM", null, null, null),
                reportRow("REPORTED", reportedCaptain, report, "NOSHOW", "TEAM", "TEAM", null, null, null)));

        dispatcher.teamReported(new TeamReportedEvent(report, MATCH, Report.ReporterType.TEAM, AUTHOR,
                Report.ReportedType.TEAM, OPPONENT, Report.ReportType.NOSHOW, NOW));

        NotificationDraft draft = drafts().getFirst();
        assertThat(draft.recipientUserId()).isEqualTo(reportedCaptain);
        assertThat(draft.type()).isEqualTo(Type.REPORT_RECEIVED);
        assertThat(draft.referenceType()).isEqualTo(ReferenceType.REPORT);
        assertThat(draft.body()).isEqualTo("Đội FC A đã report đội bạn: bùng kèo. Bạn có 24 giờ để tranh chấp.");
    }

    @Test
    void mercenaryReportFromApplicantUsesPosition() {
        UUID report = UUID.randomUUID();
        when(audience.findReportAudience(report, false)).thenReturn(List.of(
                reportRow("REPORTED", AUTHOR_CAPTAIN, report, "BAD_PLAY", "INDIVIDUAL", "TEAM", UUID.randomUUID(), null, null)));

        dispatcher.mercenaryReportCreated(new MercenaryReportCreatedEvent(report, UUID.randomUUID(), MercenarySide.APPLICANT,
                Report.ReportType.BAD_PLAY, NOW));

        assertThat(drafts().getFirst().body()).isEqualTo("Mercenary vị trí Thủ môn đã report đội bạn: chơi xấu.");
    }

    @Test
    void disputeNotifiesReporterAndEveryAdminWithDisputeReference() {
        UUID report = UUID.randomUUID();
        UUID dispute = UUID.randomUUID();
        UUID admin = UUID.randomUUID();
        when(audience.findReportAudience(report, true)).thenReturn(List.of(
                reportRow("REPORTER", AUTHOR_CAPTAIN, report, "NOSHOW", "TEAM", "TEAM", null, dispute, null),
                reportRow("REPORTED", OPPONENT_CAPTAIN, report, "NOSHOW", "TEAM", "TEAM", null, dispute, null),
                reportRow("ADMIN", admin, report, "NOSHOW", "TEAM", "TEAM", null, dispute, null)));

        dispatcher.reportDisputed(new ReportDisputedEvent(dispute, report, Report.ReporterType.TEAM, AUTHOR,
                Report.ReportedType.TEAM, OPPONENT, NOW));

        assertThat(drafts()).extracting(NotificationDraft::recipientUserId, NotificationDraft::type,
                NotificationDraft::referenceType, NotificationDraft::referenceId)
                .containsExactly(tuple(AUTHOR_CAPTAIN, Type.REPORT_DISPUTED, ReferenceType.REPORT, report),
                        tuple(admin, Type.DISPUTE_CREATED, ReferenceType.DISPUTE, dispute));
    }

    @Test
    void mercenaryDisputeUsesMercenaryDisputeReference() {
        UUID report = UUID.randomUUID();
        UUID dispute = UUID.randomUUID();
        UUID admin = UUID.randomUUID();
        when(audience.findReportAudience(report, true)).thenReturn(List.of(
                reportRow("ADMIN", admin, report, "NOSHOW", "TEAM", "INDIVIDUAL", UUID.randomUUID(), dispute, null)));

        dispatcher.reportDisputed(new ReportDisputedEvent(dispute, report, Report.ReporterType.TEAM, AUTHOR,
                Report.ReportedType.INDIVIDUAL, UUID.randomUUID(), NOW));

        assertThat(drafts()).extracting(NotificationDraft::referenceType).containsExactly(ReferenceType.MERCENARY_DISPUTE);
    }

    @Test
    void disputeResolvedRejectedHasVariantPerSide() {
        UUID report = UUID.randomUUID();
        when(audience.findReportAudience(report, false)).thenReturn(List.of(
                reportRow("REPORTER", AUTHOR_CAPTAIN, report, "NOSHOW", "TEAM", "TEAM", null, UUID.randomUUID(), "REJECTED"),
                reportRow("REPORTED", OPPONENT_CAPTAIN, report, "NOSHOW", "TEAM", "TEAM", null, UUID.randomUUID(), "REJECTED")));

        dispatcher.disputeResolved(new DisputeResolvedEvent(UUID.randomUUID(), report, Report.ReporterType.TEAM, AUTHOR,
                Report.ReportedType.TEAM, OPPONENT, DisputeResolvedEvent.Decision.REJECTED, NOW));

        List<NotificationDraft> drafts = drafts();
        assertThat(drafts).extracting(NotificationDraft::type).containsOnly(Type.DISPUTE_RESOLVED);
        assertThat(drafts.get(0).body()).startsWith("Admin bác report bùng kèo của bạn");
        assertThat(drafts.get(1).body()).contains("điểm uy tín đã bị trừ được hoàn lại");
    }

    @Test
    void verificationChangesForTeamAndUser() {
        stubTeam(AUTHOR, "FC A", AUTHOR_CAPTAIN);
        UUID user = UUID.randomUUID();

        dispatcher.teamVerificationChanged(new TeamVerificationChangedEvent(AUTHOR, true, NOW));
        dispatcher.userVerificationChanged(new UserVerificationChangedEvent(user, false, NOW));

        assertThat(allDrafts(2)).extracting(NotificationDraft::recipientUserId, NotificationDraft::type,
                NotificationDraft::referenceType)
                .containsExactly(tuple(AUTHOR_CAPTAIN, Type.VERIFICATION_BADGE_GRANTED, ReferenceType.TEAM),
                        tuple(user, Type.VERIFICATION_BADGE_REVOKED, ReferenceType.USER));
    }

    // ---------- mercenary ----------

    @Test
    void submittedNotifiesCaptainWhilePending() {
        UUID application = UUID.randomUUID();
        stubApplication(application, "PENDING", false, false);

        dispatcher.mercenaryApplicationSubmitted(new MercenaryApplicationSubmittedEvent(application, UUID.randomUUID(),
                MATCH, null, AUTHOR, UUID.randomUUID(), NOW));

        assertThat(drafts()).extracting(NotificationDraft::recipientUserId, NotificationDraft::type,
                NotificationDraft::referenceType)
                .containsExactly(tuple(AUTHOR_CAPTAIN, Type.MERCENARY_APPLICATION, ReferenceType.MERCENARY_APPLICATION));
    }

    @Test
    void submittedButNoLongerPendingIsSkipped() {
        UUID application = UUID.randomUUID();
        stubApplication(application, "WITHDRAWN", false, false);

        dispatcher.mercenaryApplicationSubmitted(new MercenaryApplicationSubmittedEvent(application, UUID.randomUUID(),
                MATCH, null, AUTHOR, UUID.randomUUID(), NOW));

        verify(writer, never()).write(any(), any());
    }

    @Test
    void approvedAndRejectedNotifyApplicant() {
        UUID application = UUID.randomUUID();
        UUID applicant = stubApplication(application, "APPROVED", false, false);

        dispatcher.mercenaryApplicationApproved(new MercenaryApplicationApprovedEvent(application, UUID.randomUUID(),
                MATCH, null, AUTHOR, applicant, NOW));
        dispatcher.mercenaryApplicationRejected(new MercenaryApplicationRejectedEvent(application, UUID.randomUUID(),
                MATCH, null, AUTHOR, applicant, MercenaryApplicationRejectedEvent.Cause.RECRUITMENT_CLOSED, NOW));

        List<NotificationDraft> drafts = allDrafts(2);
        assertThat(drafts).extracting(NotificationDraft::recipientUserId, NotificationDraft::type)
                .containsExactly(tuple(applicant, Type.MERCENARY_APPLICATION_APPROVED),
                        tuple(applicant, Type.MERCENARY_APPLICATION_REJECTED));
        assertThat(drafts.get(1).body()).contains("đã đóng");
    }

    @Test
    void applicantWithdrawingPendingApplicationIsSkippedWithoutQuery() {
        dispatcher.mercenaryApplicationWithdrawn(new MercenaryApplicationWithdrawnEvent(UUID.randomUUID(),
                UUID.randomUUID(), MATCH, null, AUTHOR, UUID.randomUUID(), MercenaryApplication.Status.PENDING,
                MercenaryApplicationWithdrawnEvent.Cause.APPLICANT, NOW));

        verifyNoInteractions(audience, writer);
    }

    @Test
    void accountDeletionWithdrawalNotifiesCaptain() {
        UUID application = UUID.randomUUID();
        stubApplication(application, "WITHDRAWN", false, false);

        dispatcher.mercenaryApplicationWithdrawn(new MercenaryApplicationWithdrawnEvent(application, UUID.randomUUID(),
                MATCH, null, AUTHOR, UUID.randomUUID(), MercenaryApplication.Status.PENDING,
                MercenaryApplicationWithdrawnEvent.Cause.ACCOUNT_DELETED, NOW));

        NotificationDraft draft = drafts().getFirst();
        assertThat(draft.recipientUserId()).isEqualTo(AUTHOR_CAPTAIN);
        assertThat(draft.type()).isEqualTo(Type.MERCENARY_WITHDRAWN);
        assertThat(draft.body()).contains("xoá tài khoản");
    }

    @Test
    void emptyIdListsSkipWithoutQuery() {
        dispatcher.mercenaryRecruitmentClosed(new MercenaryRecruitmentClosedEvent(UUID.randomUUID(), MATCH, null, AUTHOR,
                MercenaryRecruitmentClosedEvent.Cause.CAPTAIN, List.of(), NOW));
        dispatcher.mercenaryGameCancelled(new MercenaryGameCancelledEvent(MATCH, null, List.of(), NOW));

        verifyNoInteractions(audience, writer);
    }

    @Test
    void gameCancelledNotifiesEveryMercenary() {
        UUID application = UUID.randomUUID();
        UUID applicant = stubApplication(application, "APPROVED", false, false);

        dispatcher.mercenaryGameCancelled(new MercenaryGameCancelledEvent(MATCH, null, List.of(application), NOW));

        assertThat(drafts()).extracting(NotificationDraft::recipientUserId, NotificationDraft::type)
                .containsExactly(tuple(applicant, Type.MERCENARY_GAME_CANCELLED));
    }

    @Test
    void mercenaryRatingOnlyWhenRecipientRatedBack() {
        UUID application = UUID.randomUUID();
        UUID applicant = stubApplication(application, "APPROVED", true, true);

        dispatcher.mercenaryRatingCreated(new MercenaryRatingCreatedEvent(UUID.randomUUID(), application,
                MercenarySide.CAPTAIN, NOW));

        assertThat(drafts()).extracting(NotificationDraft::recipientUserId, NotificationDraft::type)
                .containsExactly(tuple(applicant, Type.RATING_RECEIVED));
    }

    @Test
    void mercenaryRatingSkippedWhenCaptainHasNotRated() {
        UUID application = UUID.randomUUID();
        stubApplication(application, "APPROVED", false, true);

        dispatcher.mercenaryRatingCreated(new MercenaryRatingCreatedEvent(UUID.randomUUID(), application,
                MercenarySide.APPLICANT, NOW));

        verify(writer, never()).write(any(), any());
    }

    // ---------- helpers ----------

    private List<NotificationDraft> drafts() {
        return allDrafts(1);
    }

    @SuppressWarnings("unchecked")
    private List<NotificationDraft> allDrafts(int calls) {
        ArgumentCaptor<List<NotificationDraft>> captor = ArgumentCaptor.forClass(List.class);
        verify(writer, times(calls)).write(captor.capture(), eq(NOW));
        return captor.getAllValues().stream().flatMap(List::stream).toList();
    }

    private void stubTeam(UUID teamId, String name, UUID captain) {
        Map<String, Object> values = new HashMap<>();
        values.put("teamId", teamId);
        values.put("teamName", name);
        values.put("captainUserId", captain);
        when(audience.findTeamAudience(new UUID[]{teamId})).thenReturn(List.of(projection(TeamAudienceRowView.class, values)));
    }

    private void stubMatch(String requestStatus, String pairingStatus, MatchAudienceRowView... rows) {
        when(audience.findMatchAudience(eq(MATCH), any(), eq(requestStatus), eq(pairingStatus))).thenReturn(List.of(rows));
    }

    private MatchAudienceRowView listed(UUID team, String name, UUID captain) {
        return row("LISTED", team, name, captain, false);
    }

    private MatchAudienceRowView row(String source, UUID team, String name, UUID captain, boolean hasRated) {
        Map<String, Object> values = new HashMap<>();
        values.put("matchId", MATCH);
        values.put("matchType", "HAS_VENUE");
        values.put("matchStatus", "CONFIRMED");
        values.put("matchStartTime", START);
        values.put("venueName", "Sân X");
        values.put("authorTeamId", AUTHOR);
        values.put("authorTeamName", "FC A");
        values.put("opponentTeamId", OPPONENT);
        values.put("source", source);
        values.put("teamId", team);
        values.put("teamName", name);
        values.put("captainUserId", captain);
        values.put("hasRated", hasRated);
        return projection(MatchAudienceRowView.class, values);
    }

    private GameAudienceRowView game(String kind, UUID recipient, UUID application, String team, String opponent,
                                     String position) {
        Map<String, Object> values = new HashMap<>();
        values.put("kind", kind);
        values.put("recipientUserId", recipient);
        values.put("matchId", MATCH);
        values.put("externalMatchId", null);
        values.put("applicationId", application);
        values.put("startTime", START);
        values.put("venueName", "Sân X");
        values.put("teamName", team);
        values.put("opponentTeamName", opponent);
        values.put("position", position);
        return projection(GameAudienceRowView.class, values);
    }

    private ReportAudienceRowView reportRow(String audienceKind, UUID recipient, UUID report, String reportType,
                                            String reporterType, String reportedType, UUID application, UUID dispute,
                                            String decision) {
        Map<String, Object> values = new HashMap<>();
        values.put("audience", audienceKind);
        values.put("recipientUserId", recipient);
        values.put("reportId", report);
        values.put("reportType", reportType);
        values.put("reporterType", reporterType);
        values.put("reportedType", reportedType);
        values.put("reporterTeamName", "TEAM".equals(reporterType) ? "FC A" : null);
        values.put("reportedTeamName", "TEAM".equals(reportedType) ? "FC B" : null);
        values.put("position", "Thủ môn");
        values.put("applicationId", application);
        values.put("disputeId", dispute);
        values.put("decision", decision);
        values.put("gameStartTime", START);
        return projection(ReportAudienceRowView.class, values);
    }

    private UUID stubApplication(UUID application, String status, boolean ratedByTeam, boolean ratedByApplicant) {
        UUID applicant = UUID.randomUUID();
        Map<String, Object> values = new HashMap<>();
        values.put("applicationId", application);
        values.put("applicationStatus", status);
        values.put("applicantId", applicant);
        values.put("position", "Thủ môn");
        values.put("recruitmentId", UUID.randomUUID());
        values.put("teamId", AUTHOR);
        values.put("teamName", "FC A");
        values.put("captainUserId", AUTHOR_CAPTAIN);
        values.put("matchId", MATCH);
        values.put("externalMatchId", null);
        values.put("gameStartTime", START);
        values.put("venueName", "Sân X");
        values.put("ratedByTeam", ratedByTeam);
        values.put("ratedByApplicant", ratedByApplicant);
        when(audience.findApplicationAudience(any())).thenReturn(List.of(projection(ApplicationAudienceRowView.class, values)));
        return applicant;
    }

    private <T> T projection(Class<T> type, Map<String, Object> values) {
        return projections.createProjection(type, values);
    }
}
