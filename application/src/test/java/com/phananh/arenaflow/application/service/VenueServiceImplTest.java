package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.CreateVenueRequest;
import com.phananh.arenaflow.application.dto.request.SearchVenueRequest;
import com.phananh.arenaflow.application.dto.request.UpdateVenueRequest;
import com.phananh.arenaflow.application.dto.response.VenueResponse;
import com.phananh.arenaflow.application.mapper.VenueMapper;
import com.phananh.arenaflow.application.service.impl.VenueServiceImpl;
import com.phananh.arenaflow.application.support.ConcurrencyConflictRetrier;
import com.phananh.arenaflow.application.support.PageRequestFactory;
import com.phananh.arenaflow.application.support.VenueGuard;
import com.phananh.arenaflow.application.support.security.CurrentActor;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.entity.user.User;
import com.phananh.arenaflow.domain.entity.venue.Venue;
import com.phananh.arenaflow.domain.projection.VenueDetailView;
import com.phananh.arenaflow.domain.projection.VenueSummaryView;
import com.phananh.arenaflow.domain.projection.WardView;
import com.phananh.arenaflow.domain.repository.AdministrativeUnitRepository;
import com.phananh.arenaflow.domain.repository.UserRepository;
import com.phananh.arenaflow.domain.repository.VenueImageRepository;
import com.phananh.arenaflow.domain.repository.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VenueServiceImplTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final AdministrativeUnitRepository unitRepository = mock(AdministrativeUnitRepository.class);
    private final VenueRepository venueRepository = mock(VenueRepository.class);
    private final VenueImageRepository imageRepository = mock(VenueImageRepository.class);
    private final ConcurrencyConflictRetrier retrier = mock(ConcurrencyConflictRetrier.class);
    private final VenueMapper venueMapper = Mappers.getMapper(VenueMapper.class);
    private final SpelAwareProxyProjectionFactory projections = new SpelAwareProxyProjectionFactory();
    private final UUID callerId = UUID.randomUUID();
    private final UUID venueId = UUID.randomUUID();
    private final UUID wardId = UUID.randomUUID();

    private VenueServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new VenueServiceImpl(userRepository, unitRepository, venueRepository, imageRepository, venueMapper,
                new PageRequestFactory(), new VenueGuard(venueRepository), retrier);
        when(retrier.execute(eq(ErrorCode.VENUE_011), any())).thenAnswer(invocation ->
                ((Supplier<Object>) invocation.getArgument(1)).get());
        when(venueRepository.save(any(Venue.class))).thenAnswer(invocation -> {
            Venue venue = invocation.getArgument(0);
            ReflectionTestUtils.setField(venue, "id", venueId);
            return venue;
        });
    }

    @Test
    void createVenueChecksUserThenWardAndBuildsResponseWithoutReread() {
        User user = User.builder().id(callerId).displayName("Creator").role(User.Role.ADMIN).build();
        WardView ward = projections.createProjection(WardView.class, Map.of(
                "wardId", wardId, "wardName", "Ward", "provinceId", UUID.randomUUID(), "provinceName", "Province"));
        when(userRepository.findByIdAndDeletedAtIsNull(callerId)).thenReturn(Optional.of(user));
        when(unitRepository.findWardWithProvince(wardId)).thenReturn(Optional.of(ward));

        VenueResponse response = service.createVenue(callerId, new CreateVenueRequest("  Venue  ", "  Address  ",
                new BigDecimal("10.1"), new BigDecimal("106.1"), wardId, "", "", ""));

        ArgumentCaptor<Venue> captured = ArgumentCaptor.forClass(Venue.class);
        verify(venueRepository).save(captured.capture());
        Venue saved = captured.getValue();
        assertThat(saved.getName()).isEqualTo("Venue");
        assertThat(saved.getAddress()).isEqualTo("Address");
        assertThat(saved.getVenueType()).isNull();
        assertThat(saved.getPhone()).isNull();
        assertThat(saved.getWebsite()).isNull();
        assertThat(saved.getCreatedByType()).isEqualTo(Venue.CreatedByType.ADMIN);
        assertThat(saved.getCreatedByUserId()).isEqualTo(callerId);
        assertThat(response.venueId()).isEqualTo(venueId);
        assertThat(response.createdByName()).isEqualTo("Creator");
        assertThat(response.ward().wardName()).isEqualTo("Ward");
        assertThat(response.images()).isEmpty();
    }

    @Test
    void createVenueReturnsUser001BeforeWardLookupForDeletedCaller() {
        when(userRepository.findByIdAndDeletedAtIsNull(callerId)).thenReturn(Optional.empty());

        assertError(() -> service.createVenue(callerId, request()), ErrorCode.USER_001);
    }

    @Test
    void createVenueReturnsUser002ForUnknownOrNonWard() {
        User user = User.builder().id(callerId).displayName("Creator").build();
        when(userRepository.findByIdAndDeletedAtIsNull(callerId)).thenReturn(Optional.of(user));
        when(unitRepository.findWardWithProvince(wardId)).thenReturn(Optional.empty());

        assertError(() -> service.createVenue(callerId, request()), ErrorCode.USER_002);
    }

    @Test
    void listVenuesSelectsIndexAwareQueryAndMapsPaginatedSummary() {
        VenueSummaryView summary = projections.createProjection(VenueSummaryView.class, Map.of(
                "venueId", venueId, "venueName", "Hoa Lư", "address", "Address",
                "latitude", BigDecimal.ONE, "longitude", BigDecimal.ONE, "status", "ACTIVE"));
        when(venueRepository.searchByStatus(eq("ACTIVE"), eq("%hoa%"), any()))
                .thenReturn(new PageImpl<>(List.of(summary), PageRequest.of(1, 1), 3));
        when(venueRepository.searchAll(eq(null), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        when(venueRepository.searchNearbyActive(eq(null), anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), anyDouble(), any()))
                .thenReturn(new PageImpl<>(List.of(summary), PageRequest.of(0, 20), 1));
        when(venueRepository.searchNearbyAny(eq("INACTIVE"), eq(null), anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), anyDouble(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        var byStatus = service.listVenues(new SearchVenueRequest(" hoa ", null, null, null, null, 2, 1));
        var all = service.listVenues(new SearchVenueRequest(null, VenueStatusFilter.ALL, null, null, null, null, null));
        var nearbyActive = service.listVenues(new SearchVenueRequest(null, null,
                new BigDecimal("10.78"), new BigDecimal("106.70"), null, null, null));
        var nearbyInactive = service.listVenues(new SearchVenueRequest(null, VenueStatusFilter.INACTIVE,
                new BigDecimal("10.78"), new BigDecimal("106.70"), new BigDecimal("3"), null, null));

        assertThat(byStatus.data()).extracting(row -> row.venueName()).containsExactly("Hoa Lư");
        assertThat(byStatus.pagination().totalItems()).isEqualTo(3);
        assertThat(all.data()).isEmpty();
        assertThat(nearbyActive.data()).hasSize(1);
        assertThat(nearbyInactive.data()).isEmpty();
        verify(venueRepository).searchByStatus(eq("ACTIVE"), eq("%hoa%"), any());
        verify(venueRepository).searchAll(eq(null), any());
        verify(venueRepository).searchNearbyActive(eq(null), eq(10.78d), eq(106.70d), eq(5d), anyDouble(),
                anyDouble(), anyDouble(), anyDouble(), any());
        verify(venueRepository).searchNearbyAny(eq("INACTIVE"), eq(null), eq(10.78d), eq(106.70d), eq(3d),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), any());
    }

    @Test
    void updateVenueChecksGuardsThenAppliesRequestedFieldsAndRereadsDetail() {
        Venue venue = Venue.builder().id(venueId).name("Old").address("Old address").latitude(BigDecimal.ONE)
                .longitude(BigDecimal.ONE).wardId(wardId).createdByUserId(callerId).status(Venue.Status.ACTIVE)
                .venueType(Venue.VenueType.SEVEN_A_SIDE).phone("0123456789").website("https://old.test").build();
        WardView replacementWard = projections.createProjection(WardView.class, Map.of(
                "wardId", UUID.randomUUID(), "wardName", "Ward", "provinceId", UUID.randomUUID(), "provinceName", "Province"));
        VenueDetailView detail = projections.createProjection(VenueDetailView.class, Map.of(
                "venueId", venueId, "venueName", "New", "address", "New address", "latitude", BigDecimal.TEN,
                "longitude", BigDecimal.valueOf(20), "status", "ACTIVE", "createdByType", "USER"));
        when(venueRepository.findById(venueId)).thenReturn(Optional.of(venue));
        when(unitRepository.findWardWithProvince(replacementWard.getWardId())).thenReturn(Optional.of(replacementWard));
        when(venueRepository.findDetailById(venueId)).thenReturn(Optional.of(detail));
        when(imageRepository.findByVenueIdOrderByDisplayOrderAsc(venueId)).thenReturn(List.of());

        VenueResponse response = service.updateVenue(venueId, new CurrentActor(callerId, false),
                new UpdateVenueRequest(" New ", " New address ", BigDecimal.TEN, BigDecimal.valueOf(20),
                        replacementWard.getWardId(), "", "", ""));

        assertThat(venue.getName()).isEqualTo("New");
        assertThat(venue.getAddress()).isEqualTo("New address");
        assertThat(venue.getLatitude()).isEqualByComparingTo("10");
        assertThat(venue.getLongitude()).isEqualByComparingTo("20");
        assertThat(venue.getWardId()).isEqualTo(replacementWard.getWardId());
        assertThat(venue.getVenueType()).isNull();
        assertThat(venue.getPhone()).isNull();
        assertThat(venue.getWebsite()).isNull();
        verify(venueRepository).save(venue);
        verify(retrier).execute(eq(ErrorCode.VENUE_011), any());
        assertThat(response.venueName()).isEqualTo("New");
    }

    @Test
    void updateVenueAppliesDocumentedGuardOrder() {
        CurrentActor owner = new CurrentActor(callerId, false);
        when(venueRepository.findById(venueId)).thenReturn(Optional.empty());
        assertError(() -> service.updateVenue(venueId, owner, new UpdateVenueRequest(null, null, null, null, null, null, null, null)),
                ErrorCode.VENUE_001);

        Venue inactiveOtherOwner = Venue.builder().createdByUserId(UUID.randomUUID()).status(Venue.Status.INACTIVE).build();
        when(venueRepository.findById(venueId)).thenReturn(Optional.of(inactiveOtherOwner));
        assertError(() -> service.updateVenue(venueId, owner, new UpdateVenueRequest(null, null, null, null, null, null, null, null)),
                ErrorCode.VENUE_013);

        Venue inactiveOwner = Venue.builder().createdByUserId(callerId).status(Venue.Status.INACTIVE).build();
        when(venueRepository.findById(venueId)).thenReturn(Optional.of(inactiveOwner));
        assertError(() -> service.updateVenue(venueId, owner, new UpdateVenueRequest(null, null, null, null, null, null, null, null)),
                ErrorCode.VENUE_002);

        Venue legacy = Venue.builder().createdByUserId(callerId).status(Venue.Status.ACTIVE).wardId(null).build();
        when(venueRepository.findById(venueId)).thenReturn(Optional.of(legacy));
        assertError(() -> service.updateVenue(venueId, owner, new UpdateVenueRequest(null, null, null, null, null, null, null, null)),
                ErrorCode.VENUE_016);
    }

    @Test
    void getVenueReturnsDetailWithImagesOrVenue001() {
        VenueDetailView view = projections.createProjection(VenueDetailView.class,
                Map.of("venueId", venueId, "venueName", "Venue", "address", "Address",
                        "latitude", BigDecimal.ONE, "longitude", BigDecimal.ONE, "status", "ACTIVE",
                        "createdByType", "USER"));
        when(venueRepository.findDetailById(venueId)).thenReturn(Optional.of(view));
        when(imageRepository.findByVenueIdOrderByDisplayOrderAsc(venueId)).thenReturn(List.of());

        assertThat(service.getVenue(venueId).venueName()).isEqualTo("Venue");

        UUID missing = UUID.randomUUID();
        when(venueRepository.findDetailById(missing)).thenReturn(Optional.empty());
        assertError(() -> service.getVenue(missing), ErrorCode.VENUE_001);
    }

    private CreateVenueRequest request() {
        return new CreateVenueRequest("Venue", "Address", BigDecimal.ONE, BigDecimal.ONE, wardId,
                null, null, null);
    }

    private static void assertError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
