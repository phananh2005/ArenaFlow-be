package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.AddRecruitmentsRequest;
import com.phananh.arenaflow.application.dto.request.CreateRecruitmentsRequest;
import com.phananh.arenaflow.application.dto.request.RecruitmentPositionRequest;
import com.phananh.arenaflow.application.dto.request.SearchRecruitmentsRequest;
import com.phananh.arenaflow.application.dto.request.UpdateRecruitmentRequest;
import com.phananh.arenaflow.application.dto.response.RecruitmentResponse;
import com.phananh.arenaflow.application.event.mercenary.MercenaryRecruitmentClosedEvent;
import com.phananh.arenaflow.application.mapper.MercenaryMapper;
import com.phananh.arenaflow.application.service.impl.MercenaryRecruitmentServiceImpl;
import com.phananh.arenaflow.application.support.ConcurrencyConflictException;
import com.phananh.arenaflow.application.support.ConcurrencyConflictRetrier;
import com.phananh.arenaflow.application.support.DataIntegrityViolationFixtures;
import com.phananh.arenaflow.application.support.PageRequestFactory;
import com.phananh.arenaflow.application.support.TeamGuard;
import com.phananh.arenaflow.application.support.mercenary.MercenaryGuard;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.projection.ProfileAreaView;
import com.phananh.arenaflow.domain.projection.RecruitContextView;
import com.phananh.arenaflow.domain.projection.RecruitmentRowView;
import com.phananh.arenaflow.domain.projection.RecruitmentWriteContextView;
import com.phananh.arenaflow.domain.repository.ExternalMatchRepository;
import com.phananh.arenaflow.domain.repository.MercenaryApplicationRepository;
import com.phananh.arenaflow.domain.repository.MercenaryRecruitmentRepository;
import com.phananh.arenaflow.domain.repository.UserRepository;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MercenaryRecruitmentServiceImplTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 12, 0);

    private final MercenaryRecruitmentRepository recruitmentRepository = mock(MercenaryRecruitmentRepository.class);
    private final MercenaryApplicationRepository applicationRepository = mock(MercenaryApplicationRepository.class);
    private final ExternalMatchRepository externalMatchRepository = mock(ExternalMatchRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final TeamGuard teamGuard = mock(TeamGuard.class);
    private final ConcurrencyConflictRetrier retrier = mock(ConcurrencyConflictRetrier.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final SpelAwareProxyProjectionFactory projections = new SpelAwareProxyProjectionFactory();
    private final UUID callerId = UUID.randomUUID();
    private final UUID matchId = UUID.randomUUID();
    private final UUID teamId = UUID.randomUUID();

    private MercenaryRecruitmentServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        service = new MercenaryRecruitmentServiceImpl(recruitmentRepository, applicationRepository,
                externalMatchRepository, userRepository,
                teamGuard, new MercenaryGuard(), retrier, Mappers.getMapper(MercenaryMapper.class),
                new PageRequestFactory(), eventPublisher, clock);
        when(retrier.execute(eq(ErrorCode.MATCH_014), any())).thenAnswer(invocation ->
                ((Supplier<Object>) invocation.getArgument(1)).get());
    }

    @Test
    void createChecksMatchThenTeamThenCaptain() {
        when(recruitmentRepository.findRecruitContext(matchId, teamId, callerId)).thenReturn(Optional.empty());
        assertError(() -> service.createRecruitments(matchId, callerId, request("Tiền vệ")), ErrorCode.MATCH_001);

        when(recruitmentRepository.findRecruitContext(matchId, teamId, callerId))
                .thenReturn(Optional.of(recruitContext(Map.of("teamId", ""))));
        assertError(() -> service.createRecruitments(matchId, callerId, request("Tiền vệ")), ErrorCode.TEAM_001);

        when(recruitmentRepository.findRecruitContext(matchId, teamId, callerId))
                .thenReturn(Optional.of(recruitContext(Map.of("teamStatus", "INACTIVE"))));
        assertError(() -> service.createRecruitments(matchId, callerId, request("Tiền vệ")), ErrorCode.TEAM_015);

        when(recruitmentRepository.findRecruitContext(matchId, teamId, callerId))
                .thenReturn(Optional.of(recruitContext(Map.of("callerCaptain", false))));
        assertError(() -> service.createRecruitments(matchId, callerId, request("Tiền vệ")), ErrorCode.TEAM_002);
        verify(recruitmentRepository, never()).insertAll(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void createRejectsPositionThatAlreadyHasActiveRecruitmentWithoutWriting() {
        when(recruitmentRepository.findRecruitContext(matchId, teamId, callerId))
                .thenReturn(Optional.of(recruitContext(Map.of("activePositions", "Hậu vệ|Tiền vệ"))));

        assertError(() -> service.createRecruitments(matchId, callerId, request("Thủ môn", "Tiền vệ")),
                ErrorCode.MERCENARY_023);
        verify(recruitmentRepository, never()).insertAll(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void createMapsUniqueIndexRaceToMercenary023() {
        when(recruitmentRepository.findRecruitContext(matchId, teamId, callerId))
                .thenReturn(Optional.of(recruitContext(Map.of())));
        when(recruitmentRepository.insertAll(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(DataIntegrityViolationFixtures.violation("idx_mercenary_recruitment_match_position"));

        assertError(() -> service.createRecruitments(matchId, callerId, request("Tiền vệ")), ErrorCode.MERCENARY_023);
    }

    @Test
    void createInsertsAllPositionsInOneStatementAndKeepsRequestOrder() {
        when(recruitmentRepository.findRecruitContext(matchId, teamId, callerId))
                .thenReturn(Optional.of(recruitContext(Map.of())));
        RecruitmentRowView midfield = row("Tiền vệ", 2);
        RecruitmentRowView keeper = row("Thủ môn", 1);
        when(recruitmentRepository.insertAll(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(midfield, keeper));

        List<RecruitmentResponse> responses = service.createRecruitments(matchId, callerId,
                new CreateRecruitmentsRequest(teamId, List.of(new RecruitmentPositionRequest("Thủ môn", 1),
                        new RecruitmentPositionRequest("Tiền vệ", 2))));

        ArgumentCaptor<String[]> positions = ArgumentCaptor.forClass(String[].class);
        ArgumentCaptor<int[]> quantities = ArgumentCaptor.forClass(int[].class);
        verify(recruitmentRepository).insertAll(eq(matchId), eq(null), eq(teamId), eq(callerId), positions.capture(),
                quantities.capture(), eq(NOW));
        assertThat(positions.getValue()).containsExactly("Thủ môn", "Tiền vệ");
        assertThat(quantities.getValue()).containsExactly(1, 2);
        assertThat(responses).extracting(RecruitmentResponse::position).containsExactly("Thủ môn", "Tiền vệ");
        assertThat(responses.get(0).status()).isEqualTo("OPEN");
        assertThat(responses.get(0).viewerRole()).isEqualTo("CAPTAIN");
        assertThat(responses.get(0).match().venue()).isNotNull();
        assertThat(responses.get(0).captainName()).isEqualTo("Captain");
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void addToExternalMatchChecksExistenceThenEndedMatch() {
        UUID externalId = UUID.randomUUID();
        AddRecruitmentsRequest request = new AddRecruitmentsRequest(List.of(new RecruitmentPositionRequest("Hậu vệ", 1)));
        when(externalMatchRepository.findRecruitContext(externalId, callerId)).thenReturn(Optional.empty());
        assertError(() -> service.addExternalRecruitments(externalId, callerId, request), ErrorCode.MERCENARY_024);

        when(externalMatchRepository.findRecruitContext(externalId, callerId)).thenReturn(Optional.of(recruitContext(
                Map.of("source", "EXTERNAL", "matchStatus", "CANCELLED", "opponentTeamId", ""))));
        assertError(() -> service.addExternalRecruitments(externalId, callerId, request), ErrorCode.MERCENARY_004);
    }

    @Test
    void updateQuantityGuardsBeforeWriting() {
        UUID id = UUID.randomUUID();
        when(recruitmentRepository.findWriteContext(id, callerId, NOW)).thenReturn(Optional.empty());
        assertError(() -> service.updateQuantity(id, callerId, new UpdateRecruitmentRequest(3)), ErrorCode.MERCENARY_001);

        when(recruitmentRepository.findWriteContext(id, callerId, NOW))
                .thenReturn(Optional.of(writeContext(Map.of("effectiveStatus", "EXPIRED"))));
        assertError(() -> service.updateQuantity(id, callerId, new UpdateRecruitmentRequest(3)), ErrorCode.MERCENARY_007);

        when(recruitmentRepository.findWriteContext(id, callerId, NOW))
                .thenReturn(Optional.of(writeContext(Map.of("quantityApproved", 2))));
        assertError(() -> service.updateQuantity(id, callerId, new UpdateRecruitmentRequest(1)), ErrorCode.MERCENARY_008);

        when(recruitmentRepository.findWriteContext(id, callerId, NOW))
                .thenReturn(Optional.of(writeContext(Map.of("gameStart", NOW))));
        assertError(() -> service.updateQuantity(id, callerId, new UpdateRecruitmentRequest(4)), ErrorCode.MERCENARY_013);
        verify(recruitmentRepository, never()).updateQuantity(any(), anyInt(), anyInt(), anyInt(), any());
    }

    @Test
    void updateQuantityCanDecreaseAfterStartAndReturnsFilledWhenEqualToApproved() {
        UUID id = UUID.randomUUID();
        when(recruitmentRepository.findWriteContext(id, callerId, NOW))
                .thenReturn(Optional.of(writeContext(Map.of("gameStart", NOW.minusMinutes(5),
                        "quantityNeeded", 3, "quantityApproved", 2))));
        when(recruitmentRepository.updateQuantity(id, 2, 2, 3, NOW)).thenReturn(1);

        RecruitmentResponse response = service.updateQuantity(id, callerId, new UpdateRecruitmentRequest(2));

        assertThat(response.status()).isEqualTo("FILLED");
        assertThat(response.quantityNeeded()).isEqualTo(2);
        assertThat(response.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void updateQuantitySameValueDoesNotWrite() {
        UUID id = UUID.randomUUID();
        when(recruitmentRepository.findWriteContext(id, callerId, NOW))
                .thenReturn(Optional.of(writeContext(Map.of("quantityNeeded", 2))));

        RecruitmentResponse response = service.updateQuantity(id, callerId, new UpdateRecruitmentRequest(2));

        assertThat(response.quantityNeeded()).isEqualTo(2);
        verify(recruitmentRepository, never()).updateQuantity(any(), anyInt(), anyInt(), anyInt(), any());
    }

    @Test
    void updateQuantityCasMissSignalsRetry() {
        UUID id = UUID.randomUUID();
        when(recruitmentRepository.findWriteContext(id, callerId, NOW))
                .thenReturn(Optional.of(writeContext(Map.of())));
        when(recruitmentRepository.updateQuantity(any(), anyInt(), anyInt(), anyInt(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.updateQuantity(id, callerId, new UpdateRecruitmentRequest(4)))
                .isInstanceOf(ConcurrencyConflictException.class);
    }

    @Test
    void closeAllowsInactiveTeamRejectsPendingAndPublishesEvent() {
        UUID id = UUID.randomUUID();
        UUID rejected = UUID.randomUUID();
        when(recruitmentRepository.findWriteContext(id, callerId, NOW))
                .thenReturn(Optional.of(writeContext(Map.of("teamStatus", "INACTIVE", "recruitmentId", id))));
        when(recruitmentRepository.closeIfActive(id, NOW)).thenReturn(1);
        when(applicationRepository.rejectPendingByRecruitment(id, NOW)).thenReturn(List.of(rejected));

        RecruitmentResponse response = service.close(id, callerId);

        assertThat(response.status()).isEqualTo("CLOSED");
        assertThat(response.closedAt()).isEqualTo(NOW);
        assertThat(response.rejectedApplicationCount()).isEqualTo(1);
        ArgumentCaptor<MercenaryRecruitmentClosedEvent> event = ArgumentCaptor.forClass(MercenaryRecruitmentClosedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().cause()).isEqualTo(MercenaryRecruitmentClosedEvent.Cause.CAPTAIN);
        assertThat(event.getValue().rejectedApplicationIds()).containsExactly(rejected);
    }

    @Test
    void closeOfDisbandedTeamIsRejected() {
        UUID id = UUID.randomUUID();
        when(recruitmentRepository.findWriteContext(id, callerId, NOW))
                .thenReturn(Optional.of(writeContext(Map.of("teamStatus", "DISBANDED"))));

        assertError(() -> service.close(id, callerId), ErrorCode.TEAM_016);
    }

    @Test
    void searchWithoutCoordinatesNeedsProfileWard() {
        when(userRepository.findProfileArea(callerId)).thenReturn(Optional.empty());
        assertError(() -> service.search(callerId, new SearchRecruitmentsRequest(null, null, null, null, null, null)),
                ErrorCode.USER_001);

        when(userRepository.findProfileArea(callerId)).thenReturn(Optional.of(area(null, null)));
        assertError(() -> service.search(callerId, new SearchRecruitmentsRequest(null, null, null, null, null, null)),
                ErrorCode.MERCENARY_018);
    }

    @Test
    void searchWithoutCoordinatesUsesProvinceAndWard() {
        UUID wardId = UUID.randomUUID();
        UUID provinceId = UUID.randomUUID();
        when(userRepository.findProfileArea(callerId)).thenReturn(Optional.of(area(wardId, provinceId)));
        when(recruitmentRepository.searchByProvince(eq(callerId), eq("Tiền vệ"), eq(NOW), eq(provinceId), eq(wardId),
                any())).thenReturn(Page.empty());

        service.search(callerId, new SearchRecruitmentsRequest(null, null, null, "Tiền vệ", 1, 20));

        verify(recruitmentRepository, never()).searchNearby(any(), any(), any(), anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), any());
    }

    @Test
    void searchWithCoordinatesUsesBoundingBoxAndDefaultRadius() {
        when(recruitmentRepository.searchNearby(any(), any(), any(), anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), anyDouble(), any())).thenReturn(new PageImpl<>(List.of()));

        service.search(callerId, new SearchRecruitmentsRequest(10.0, 106.0, null, null, null, null));

        verify(recruitmentRepository).searchNearby(eq(callerId), eq(null), eq(NOW), eq(10.0), eq(106.0), eq(3.0),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), any());
        verifyNoInteractions(userRepository);
    }

    @Test
    void getRecruitmentNotFound() {
        UUID id = UUID.randomUUID();
        when(recruitmentRepository.findView(id, callerId, NOW, null, null)).thenReturn(Optional.empty());

        assertError(() -> service.getRecruitment(id, callerId, null, null), ErrorCode.MERCENARY_001);
    }

    private CreateRecruitmentsRequest request(String... positions) {
        return new CreateRecruitmentsRequest(teamId, Arrays.stream(positions)
                .map(p -> new RecruitmentPositionRequest(p, 1)).toList());
    }

    private RecruitContextView recruitContext(Map<String, Object> overrides) {
        Map<String, Object> values = new HashMap<>();
        values.put("source", "SYSTEM");
        values.put("matchId", matchId);
        values.put("matchStatus", "CONFIRMED");
        values.put("gameStart", NOW.plusDays(1));
        values.put("gameEnd", NOW.plusDays(1).plusMinutes(90));
        values.put("authorTeamId", teamId);
        values.put("opponentTeamId", UUID.randomUUID());
        values.put("skillLevels", "[]");
        values.put("teamId", teamId);
        values.put("teamStatus", "ACTIVE");
        values.put("teamName", "FC A");
        values.put("callerCaptain", true);
        values.put("callerName", "Captain");
        values.put("callerDeleted", false);
        values.put("venueId", UUID.randomUUID());
        values.put("venueName", "Sân A");
        values.put("wardName", "Phường 1");
        values.put("provinceName", "TP HCM");
        values.put("activePositions", "");
        values.putAll(overrides);
        if ("".equals(values.get("teamId"))) {
            values.put("teamId", null);
        }
        if ("".equals(values.get("opponentTeamId"))) {
            values.put("opponentTeamId", null);
        }
        return projections.createProjection(RecruitContextView.class, values);
    }

    private RecruitmentWriteContextView writeContext(Map<String, Object> overrides) {
        Map<String, Object> values = new HashMap<>();
        values.put("recruitmentId", UUID.randomUUID());
        values.put("matchId", matchId);
        values.put("teamId", teamId);
        values.put("teamName", "FC A");
        values.put("teamStatus", "ACTIVE");
        values.put("position", "Tiền vệ");
        values.put("quantityNeeded", 3);
        values.put("quantityApproved", 1);
        values.put("status", "OPEN");
        values.put("effectiveStatus", "OPEN");
        values.put("source", "SYSTEM");
        values.put("gameStatus", "CONFIRMED");
        values.put("gameStart", NOW.plusDays(1));
        values.put("gameEnd", NOW.plusDays(1).plusMinutes(90));
        values.put("skillLevels", "[]");
        values.put("venueId", UUID.randomUUID());
        values.put("venueName", "Sân A");
        values.put("venueLatitude", BigDecimal.ONE);
        values.put("venueLongitude", BigDecimal.ONE);
        values.put("captainName", "Captain");
        values.put("viewerIsCaptain", true);
        values.put("pendingCount", 2L);
        values.put("createdAt", NOW.minusDays(1));
        values.put("updatedAt", NOW.minusDays(1));
        values.putAll(overrides);
        return projections.createProjection(RecruitmentWriteContextView.class, values);
    }

    private RecruitmentRowView row(String position, int quantity) {
        return projections.createProjection(RecruitmentRowView.class, Map.of("id", UUID.randomUUID(),
                "position", position, "quantityNeeded", quantity, "createdAt", NOW));
    }

    private ProfileAreaView area(UUID wardId, UUID provinceId) {
        Map<String, Object> values = new HashMap<>();
        values.put("wardId", wardId);
        values.put("provinceId", provinceId);
        values.put("deleted", false);
        return projections.createProjection(ProfileAreaView.class, values);
    }

    private static void assertError(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }
}
