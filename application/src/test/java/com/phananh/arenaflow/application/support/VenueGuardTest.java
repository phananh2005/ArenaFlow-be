package com.phananh.arenaflow.application.support;

import com.phananh.arenaflow.application.support.security.CurrentActor;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.entity.venue.Venue;
import com.phananh.arenaflow.domain.repository.VenueRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VenueGuardTest {

    private final VenueRepository venueRepository = mock(VenueRepository.class);
    private final VenueGuard guard = new VenueGuard(venueRepository);
    private final UUID venueId = UUID.randomUUID();
    private final UUID creatorId = UUID.randomUUID();

    private static void assertError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }

    @Test
    void findVenueAndActiveVenueUseVenue001ThenVenue002() {
        when(venueRepository.findById(venueId)).thenReturn(Optional.empty());
        assertError(() -> guard.findVenue(venueId), ErrorCode.VENUE_001);

        Venue inactive = Venue.builder().status(Venue.Status.INACTIVE).build();
        when(venueRepository.findById(venueId)).thenReturn(Optional.of(inactive));
        assertError(() -> guard.activeVenue(venueId), ErrorCode.VENUE_002);
    }

    @Test
    void writableVenueAllowsAdminButRejectsRegularUserOnInactiveVenue() {
        Venue inactive = Venue.builder().status(Venue.Status.INACTIVE).build();

        assertError(() -> guard.requireWritable(inactive, new CurrentActor(UUID.randomUUID(), false)), ErrorCode.VENUE_002);
        guard.requireWritable(inactive, new CurrentActor(UUID.randomUUID(), true));
    }

    @Test
    void ownerOrAdminAllowsOnlyCreatorOrAdmin() {
        Venue venue = Venue.builder().createdByUserId(creatorId).build();

        guard.requireOwnerOrAdmin(venue, new CurrentActor(creatorId, false));
        guard.requireOwnerOrAdmin(venue, new CurrentActor(UUID.randomUUID(), true));
        assertError(() -> guard.requireOwnerOrAdmin(venue, new CurrentActor(UUID.randomUUID(), false)), ErrorCode.VENUE_013);
    }
}
