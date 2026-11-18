package com.phananh.arenaflow.application.service.impl;

import com.phananh.arenaflow.application.dto.request.CreateVenueRequest;
import com.phananh.arenaflow.application.dto.request.SearchVenueRequest;
import com.phananh.arenaflow.application.dto.request.UpdateVenueRequest;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.dto.response.VenueResponse;
import com.phananh.arenaflow.application.dto.response.VenueSummaryResponse;
import com.phananh.arenaflow.application.dto.response.WardResponse;
import com.phananh.arenaflow.application.mapper.VenueMapper;
import com.phananh.arenaflow.application.service.VenueService;
import com.phananh.arenaflow.application.service.VenueStatusFilter;
import com.phananh.arenaflow.application.support.PageRequestFactory;
import com.phananh.arenaflow.application.support.ConcurrencyConflictRetrier;
import com.phananh.arenaflow.application.support.VenueGuard;
import com.phananh.arenaflow.application.support.security.CurrentActor;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.entity.user.User;
import com.phananh.arenaflow.domain.entity.venue.Venue;
import com.phananh.arenaflow.domain.projection.WardView;
import com.phananh.arenaflow.domain.repository.AdministrativeUnitRepository;
import com.phananh.arenaflow.domain.repository.UserRepository;
import com.phananh.arenaflow.domain.repository.VenueImageRepository;
import com.phananh.arenaflow.domain.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VenueServiceImpl implements VenueService {

    private final UserRepository userRepository;
    private final AdministrativeUnitRepository administrativeUnitRepository;
    private final VenueRepository venueRepository;
    private final VenueImageRepository venueImageRepository;
    private final VenueMapper venueMapper;
    private final PageRequestFactory pageRequestFactory;
    private final VenueGuard venueGuard;
    private final ConcurrencyConflictRetrier concurrencyConflictRetrier;

    /** Q1 caller, Q2 ward/province, Q3 insert; returns from data already read without a fourth query. */
    @Override
    @Transactional
    public VenueResponse createVenue(UUID callerId, CreateVenueRequest request) {
        User creator = userRepository.findByIdAndDeletedAtIsNull(callerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_001));
        WardView ward = administrativeUnitRepository.findWardWithProvince(request.wardId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_002));
        Venue venue = venueRepository.save(Venue.builder()
                .name(request.venueName().trim())
                .address(request.address().trim())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .wardId(request.wardId())
                .venueType(emptyToNull(request.venueType()) == null ? null : Venue.VenueType.valueOf(request.venueType()))
                .phone(emptyToNull(request.phone()))
                .website(emptyToNull(request.website()))
                .createdByType(creator.getRole() == User.Role.ADMIN ? Venue.CreatedByType.ADMIN : Venue.CreatedByType.USER)
                .createdByUserId(callerId)
                .build());
        return new VenueResponse(venue.getId(), venue.getName(), venue.getAddress(), venue.getLatitude(), venue.getLongitude(),
                new WardResponse(ward.getWardId(), ward.getWardName(), ward.getProvinceId(), ward.getProvinceName()),
                venue.getVenueType() == null ? null : venue.getVenueType().name(), venue.getPhone(), venue.getWebsite(),
                venue.getStatus().name(), venue.getCreatedByType().name(), callerId, creator.getDisplayName(),
                venue.getLastSelectedAt(), venue.getAdminVerifiedInactiveAt(), venue.getCreatedAt(), venue.getUpdatedAt(), List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public VenueResponse getVenue(UUID venueId) {
        return venueRepository.findDetailById(venueId)
                .map(view -> venueMapper.toResponse(view, venueImageRepository.findByVenueIdOrderByDisplayOrderAsc(venueId)))
                .orElseThrow(() -> new BusinessException(ErrorCode.VENUE_001));
    }

    /** Q1 page + Q2 count; select the indexed native query path for each search shape. */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<VenueSummaryResponse> listVenues(SearchVenueRequest request) {
        var pageable = pageRequestFactory.create(request.page(), request.pageSize());
        String qPattern = request.q() == null || request.q().isEmpty() ? null : "%" + request.q() + "%";
        Page<com.phananh.arenaflow.domain.projection.VenueSummaryView> venues;
        if (request.lat() == null) {
            venues = request.status() == VenueStatusFilter.ALL
                    ? venueRepository.searchAll(qPattern, pageable)
                    : venueRepository.searchByStatus(request.status().toQueryValue(), qPattern, pageable);
        } else {
            double lat = request.lat().doubleValue();
            double lng = request.lng().doubleValue();
            double radiusKm = request.radiusKm().doubleValue();
            double latitudeDelta = radiusKm / 111.32d;
            double longitudeDelta = radiusKm / (111.32d * Math.cos(Math.toRadians(lat)));
            venues = request.status() == VenueStatusFilter.ACTIVE
                    ? venueRepository.searchNearbyActive(qPattern, lat, lng, radiusKm, lat - latitudeDelta,
                    lat + latitudeDelta, lng - longitudeDelta, lng + longitudeDelta, pageable)
                    : venueRepository.searchNearbyAny(request.status().toQueryValue(), qPattern, lat, lng, radiusKm,
                    lat - latitudeDelta, lat + latitudeDelta, lng - longitudeDelta, lng + longitudeDelta, pageable);
        }
        return PageResponse.from(venues, venueMapper::toSummary, request.page(), request.pageSize());
    }

    @Override
    public VenueResponse updateVenue(UUID venueId, CurrentActor actor, UpdateVenueRequest request) {
        return concurrencyConflictRetrier.execute(ErrorCode.VENUE_011, () -> {
            Venue venue = venueGuard.findVenue(venueId);
            venueGuard.requireOwnerOrAdmin(venue, actor);
            venueGuard.requireWritable(venue, actor);
            if (venue.getWardId() == null && request.wardId() == null) {
                throw new BusinessException(ErrorCode.VENUE_016);
            }
            if (request.wardId() != null) {
                administrativeUnitRepository.findWardWithProvince(request.wardId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.USER_002));
                venue.setWardId(request.wardId());
            }
            if (request.venueName() != null) venue.setName(request.venueName().trim());
            if (request.address() != null) venue.setAddress(request.address().trim());
            if (request.latitude() != null) venue.setLatitude(request.latitude());
            if (request.longitude() != null) venue.setLongitude(request.longitude());
            if (request.venueType() != null) venue.setVenueType(emptyToNull(request.venueType()) == null ? null
                    : Venue.VenueType.valueOf(request.venueType()));
            if (request.phone() != null) venue.setPhone(emptyToNull(request.phone()));
            if (request.website() != null) venue.setWebsite(emptyToNull(request.website()));
            venueRepository.save(venue);
            // Hibernate auto-flushes the managed update before the native detail projection below.
            return getVenue(venueId);
        });
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value.trim();
    }
}
