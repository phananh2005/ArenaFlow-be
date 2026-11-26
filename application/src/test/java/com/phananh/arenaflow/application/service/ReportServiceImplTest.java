package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.CreateChatReportRequest;
import com.phananh.arenaflow.application.dto.request.CreateReportRequest;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.dto.response.ReportResponse;
import com.phananh.arenaflow.application.event.reputation.TeamReportedEvent;
import com.phananh.arenaflow.application.mapper.ReputationMapper;
import com.phananh.arenaflow.application.service.impl.ReportServiceImpl;
import com.phananh.arenaflow.application.support.ConcurrencyConflictRetrier;
import com.phananh.arenaflow.application.support.PageRequestFactory;
import com.phananh.arenaflow.application.support.TeamGuard;
import com.phananh.arenaflow.application.support.chat.ConversationAccess;
import com.phananh.arenaflow.application.support.chat.ConversationAccessPolicy;
import com.phananh.arenaflow.application.support.reputation.TeamVerificationRefresher;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.entity.chat.Conversation;
import com.phananh.arenaflow.domain.entity.rating.Report;
import com.phananh.arenaflow.domain.projection.ChatReportContextView;
import com.phananh.arenaflow.domain.projection.ReportContextView;
import com.phananh.arenaflow.domain.projection.TeamReportView;
import com.phananh.arenaflow.domain.repository.MatchRepository;
import com.phananh.arenaflow.domain.repository.ReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static com.phananh.arenaflow.application.support.DataIntegrityViolationFixtures.violation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportServiceImplTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T14:10:00Z"), ZoneId.systemDefault());
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 21, 10);
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 12, 18, 0);

    private final MatchRepository matchRepository = mock(MatchRepository.class);
    private final ReportRepository reportRepository = mock(ReportRepository.class);
    private final TeamGuard teamGuard = mock(TeamGuard.class);
    private final ReputationService reputationService = mock(ReputationService.class);
    private final TeamVerificationRefresher refresher = mock(TeamVerificationRefresher.class);
    private final ConcurrencyConflictRetrier retrier = mock(ConcurrencyConflictRetrier.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final ConversationAccessPolicy policy = mock(ConversationAccessPolicy.class);
    private final SpelAwareProxyProjectionFactory projections = new SpelAwareProxyProjectionFactory();
    private final UUID matchId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID author = UUID.randomUUID();
    private final UUID opponent = UUID.randomUUID();
    private final UUID reportId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();

    private ReportService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(policy.contextType()).thenReturn(Conversation.ContextType.TEAM_MATCH);
        service = new ReportServiceImpl(matchRepository, reportRepository, teamGuard, reputationService, refresher,
                retrier, Mappers.getMapper(ReputationMapper.class), new PageRequestFactory(), publisher,
                List.of(policy), CLOCK);
        when(retrier.execute(eq(ErrorCode.MATCH_014), any())).thenAnswer(invocation ->
                ((Supplier<Object>) invocation.getArgument(1)).get());
        when(reportRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            Report report = invocation.getArgument(0);
            ReflectionTestUtils.setField(report, "id", reportId);
            ReflectionTestUtils.setField(report, "createdAt", NOW);
            return report;
        });
    }

    @Test
    void createReportGuardOrder() {
        when(matchRepository.findReportContext(matchId, callerId, "NOSHOW")).thenReturn(Optional.empty());
        assertCode(() -> service.createReport(matchId, callerId, noShow()), ErrorCode.MATCH_001);

        stub(context("ARCHIVED", false, false, "ACTIVE", opponent, false));
        assertCode(() -> service.createReport(matchId, callerId, noShow()), ErrorCode.TEAM_002);

        stub(context("OPEN", true, true, "INACTIVE", opponent, false));
        assertCode(() -> service.createReport(matchId, callerId, noShow()), ErrorCode.TEAM_015);

        stub(context("OPEN", true, true, "ACTIVE", opponent, false));
        assertCode(() -> service.createReport(matchId, callerId, noShow()), ErrorCode.REPORT_005);

        stub(context("CONFIRMED", true, false, "ACTIVE", opponent, false));
        assertCode(() -> service.createReport(matchId, callerId, noShow()), ErrorCode.REPORT_002);

        stub(context("CANCELLED", true, false, "ACTIVE", null, false));
        assertCode(() -> service.createReport(matchId, callerId, noShow()), ErrorCode.REPORT_002);

        stub(context("CANCELLED", true, false, "ACTIVE", opponent, false));
        assertCode(() -> service.createReport(matchId, callerId, noShow()), ErrorCode.REPORT_003);

        stub(context("COMPLETED", true, false, "ACTIVE", opponent, true));
        assertCode(() -> service.createReport(matchId, callerId, noShow()), ErrorCode.REPORT_004);
        verify(reportRepository, never()).saveAndFlush(any());
    }

    @Test
    void uniqueIndexRaceIsReport004WithoutPenalty() {
        stub(context("COMPLETED", true, false, "ACTIVE", opponent, false));
        doThrow(violation("idx_report_unique")).when(reportRepository).saveAndFlush(any());

        assertCode(() -> service.createReport(matchId, callerId, noShow()), ErrorCode.REPORT_004);
        verify(reputationService, never()).applyReportPenalty(any(), any(), any());
    }

    @Test
    void createReportPenalizesReportedTeamAndRecordsActualPoints() {
        stub(context("ARCHIVED", false, true, "ACTIVE", opponent, false));
        when(reputationService.applyReportPenalty(author, Report.ReportType.NOSHOW, reportId)).thenReturn(5);

        ReportResponse response = service.createReport(matchId, callerId, noShow());

        ArgumentCaptor<Report> saved = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).saveAndFlush(saved.capture());
        Report report = saved.getValue();
        assertThat(report.getReporterId()).isEqualTo(opponent);
        assertThat(report.getReportedId()).isEqualTo(author);
        assertThat(report.getStatus()).isEqualTo(Report.Status.AUTO_ACCEPTED);
        assertThat(report.getCreatedByUserId()).isEqualTo(callerId);
        assertThat(report.getReliabilityPenalty()).isEqualTo(5);
        InOrder order = inOrder(reportRepository, reputationService, publisher, refresher);
        order.verify(reportRepository).saveAndFlush(any());
        order.verify(reputationService).applyReportPenalty(author, Report.ReportType.NOSHOW, reportId);
        order.verify(publisher).publishEvent(new TeamReportedEvent(reportId, matchId, opponent, author,
                Report.ReportType.NOSHOW, NOW));
        order.verify(refresher).refreshAfterCommit(List.of(author));
        assertThat(response.direction()).isEqualTo("SENT");
        assertThat(response.counterpartTeam().teamId()).isEqualTo(author);
        assertThat(response.counterpartTeam().name()).isEqualTo("Author FC");
        assertThat(response.reliabilityPenalty()).isNull();
        assertThat(response.status()).isEqualTo("AUTO_ACCEPTED");
        assertThat(response.disputeDeadlineAt()).isEqualTo(NOW.plusHours(24));
        assertThat(response.canDispute()).isFalse();
        assertThat(response.matchStartsAt()).isEqualTo(START);
    }

    @Test
    void cancelledMatchAfterPairingAcceptsBadCommunication() {
        when(matchRepository.findReportContext(matchId, callerId, "BAD_COMMUNICATION"))
                .thenReturn(Optional.of(context("CANCELLED", true, false, "ACTIVE", opponent, false)));

        ReportResponse response = service.createReport(matchId, callerId,
                new CreateReportRequest("BAD_COMMUNICATION", "Không phản hồi"));

        assertThat(response.reportType()).isEqualTo("BAD_COMMUNICATION");
        assertThat(response.disputeDeadlineAt()).isNull();
    }

    @Test
    void chatReportGuardOrder() {
        when(reportRepository.findChatReportContext(conversationId, callerId, NOW)).thenReturn(Optional.empty());
        assertCode(() -> service.createChatReport(conversationId, callerId, chat()), ErrorCode.CHAT_002);

        stubChat(chatContext("TEAM_MATCH", true, true, false, "ACTIVE"));
        assertCode(() -> service.createChatReport(conversationId, callerId, chat()), ErrorCode.USER_001);

        stubChat(chatContext("TEAM_MATCH", false, true, false, "ACTIVE"));
        access(null, false);
        assertCode(() -> service.createChatReport(conversationId, callerId, chat()), ErrorCode.CHAT_003);

        stubChat(chatContext("TEAM_MATCH", false, true, false, "ACTIVE"));
        access(Conversation.Party.A, true);
        assertCode(() -> service.createChatReport(conversationId, callerId, chat()), ErrorCode.TEAM_016);

        stubChat(chatContext("TEAM_MATCH", false, true, false, "INACTIVE"));
        access(Conversation.Party.A, false);
        assertCode(() -> service.createChatReport(conversationId, callerId, chat()), ErrorCode.TEAM_015);

        stubChat(chatContext("TEAM_MATCH", false, true, true, "ACTIVE"));
        assertCode(() -> service.createChatReport(conversationId, callerId, chat()), ErrorCode.REPORT_005);

        Map<String, Object> noMessages = chatValues("TEAM_MATCH", false, true, false, "ACTIVE");
        noMessages.put("hasMessages", false);
        stubChat(projections.createProjection(ChatReportContextView.class, noMessages));
        assertCode(() -> service.createChatReport(conversationId, callerId, chat()), ErrorCode.REPORT_010);

        Map<String, Object> reported = chatValues("TEAM_MATCH", false, true, false, "ACTIVE");
        reported.put("AReportedB", true);
        stubChat(projections.createProjection(ChatReportContextView.class, reported));
        assertCode(() -> service.createChatReport(conversationId, callerId, chat()), ErrorCode.REPORT_004);
        verify(reportRepository, never()).saveAndFlush(any());
    }

    @Test
    void chatContextTypeWithoutPolicyIsProgrammingError() {
        stubChat(chatContext("MERCENARY_APPLICATION", false, true, false, "ACTIVE"));

        assertThatThrownBy(() -> service.createChatReport(conversationId, callerId, chat()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MERCENARY_APPLICATION");
        verify(reportRepository, never()).saveAndFlush(any());
    }

    @Test
    void chatReportFromPartyBTargetsPartyAAsBadCommunicationOfConversationMatch() {
        Map<String, Object> values = chatValues("TEAM_MATCH", false, false, true, "ACTIVE");
        values.put("AReportedB", true);
        stubChat(projections.createProjection(ChatReportContextView.class, values));
        access(Conversation.Party.B, false);
        when(reputationService.applyReportPenalty(author, Report.ReportType.BAD_COMMUNICATION, reportId)).thenReturn(2);

        ReportResponse response = service.createChatReport(conversationId, callerId, chat());

        ArgumentCaptor<Report> saved = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getMatchId()).isEqualTo(matchId);
        assertThat(saved.getValue().getReporterId()).isEqualTo(opponent);
        assertThat(saved.getValue().getReportedId()).isEqualTo(author);
        assertThat(saved.getValue().getReportType()).isEqualTo(Report.ReportType.BAD_COMMUNICATION);
        assertThat(saved.getValue().getReliabilityPenalty()).isEqualTo(2);
        verify(refresher).refreshAfterCommit(List.of(author));
        assertThat(response.counterpartTeam().name()).isEqualTo("Author FC");
        assertThat(response.matchStartsAt()).isEqualTo(START);
    }

    @Test
    void listTeamReportsChecksCaptainFirstThenPagesByDirection() {
        when(reportRepository.findReceivedReports(author, 5, 5)).thenReturn(List.of(teamReport()));
        when(reportRepository.countReceivedReports(author)).thenReturn(6L);

        PageResponse<ReportResponse> page = service.listTeamReports(author, callerId, ReportDirection.RECEIVED, 2, 5);

        InOrder order = inOrder(teamGuard, reportRepository);
        order.verify(teamGuard).requireActiveCaptain(author, callerId);
        order.verify(reportRepository).findReceivedReports(author, 5, 5);
        assertThat(page.pagination().totalItems()).isEqualTo(6);
        assertThat(page.data()).singleElement().satisfies(report -> {
            assertThat(report.direction()).isEqualTo("RECEIVED");
            assertThat(report.reliabilityPenalty()).isEqualTo(5);
            assertThat(report.canDispute()).isTrue();
        });
        verify(reportRepository, never()).findSentReports(any(), eq(5), eq(5L));
    }

    private CreateReportRequest noShow() {
        return new CreateReportRequest("NOSHOW", "Không đến");
    }

    private CreateChatReportRequest chat() {
        return new CreateChatReportRequest("Chửi bới");
    }

    private void stub(ReportContextView view) {
        when(matchRepository.findReportContext(matchId, callerId, "NOSHOW")).thenReturn(Optional.of(view));
    }

    private void stubChat(ChatReportContextView view) {
        when(reportRepository.findChatReportContext(conversationId, callerId, NOW)).thenReturn(Optional.of(view));
    }

    private void access(Conversation.Party party, boolean readOnly) {
        when(policy.resolve(any(), eq(callerId), eq(NOW))).thenReturn(new ConversationAccess(party, party != null,
                false, readOnly));
    }

    private ReportContextView context(String status, boolean authorCaptain, boolean opponentCaptain,
                                      String callerTeamStatus, UUID opponentTeamId, boolean ownReported) {
        Map<String, Object> values = new HashMap<>();
        values.put("matchId", matchId);
        values.put("status", status);
        values.put("matchStartTime", START);
        values.put("authorTeamId", author);
        values.put("opponentTeamId", opponentTeamId);
        values.put("authorTeamStatus", authorCaptain ? callerTeamStatus : "ACTIVE");
        values.put("opponentTeamStatus", opponentCaptain ? callerTeamStatus : "ACTIVE");
        values.put("authorTeamName", "Author FC");
        values.put("opponentTeamName", "Opponent FC");
        values.put("callerAuthorCaptain", authorCaptain);
        values.put("callerOpponentCaptain", opponentCaptain);
        values.put("authorReportedType", authorCaptain && ownReported);
        values.put("opponentReportedType", !authorCaptain && ownReported);
        return projections.createProjection(ReportContextView.class, values);
    }

    private ChatReportContextView chatContext(String type, boolean callerDeleted, boolean callerACaptain,
                                              boolean callerBCaptain, String callerTeamStatus) {
        return projections.createProjection(ChatReportContextView.class,
                chatValues(type, callerDeleted, callerACaptain, callerBCaptain, callerTeamStatus));
    }

    private Map<String, Object> chatValues(String type, boolean callerDeleted, boolean callerACaptain,
                                           boolean callerBCaptain, String callerTeamStatus) {
        Map<String, Object> values = new HashMap<>();
        values.put("id", conversationId);
        values.put("contextType", type);
        values.put("matchId", matchId);
        values.put("partyAType", "TEAM");
        values.put("partyAId", author);
        values.put("partyBType", "TEAM");
        values.put("partyBId", opponent);
        values.put("callerDeleted", callerDeleted);
        values.put("partyAName", "Author FC");
        values.put("partyBName", "Opponent FC");
        values.put("callerPartyACaptain", callerACaptain);
        values.put("callerPartyBCaptain", callerBCaptain);
        values.put("partyATeamStatus", callerTeamStatus);
        values.put("partyBTeamStatus", callerTeamStatus);
        values.put("matchStartTime", START);
        values.put("hasMessages", true);
        values.put("AReportedB", false);
        values.put("BReportedA", false);
        return values;
    }

    private TeamReportView teamReport() {
        Map<String, Object> values = new HashMap<>();
        values.put("reportId", reportId);
        values.put("matchId", matchId);
        values.put("matchStartTime", START);
        values.put("counterpartTeamId", opponent);
        values.put("counterpartTeamName", "Opponent FC");
        values.put("reportType", "NOSHOW");
        values.put("reason", "Không đến");
        values.put("status", "AUTO_ACCEPTED");
        values.put("reliabilityPenalty", 5);
        values.put("createdAt", NOW.minusHours(1));
        return projections.createProjection(TeamReportView.class, values);
    }

    private static void assertCode(Runnable call, ErrorCode code) {
        assertThatThrownBy(call::run).isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode()).isEqualTo(code);
    }
}
