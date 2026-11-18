package com.phananh.arenaflow.application.support;

import com.phananh.arenaflow.application.support.security.CurrentActor;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.entity.venue.Venue;
import com.phananh.arenaflow.domain.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** One source for VENUE existence, writable-state and ownership guards. */
@Component
@RequiredArgsConstructor
public class VenueGuard {

    private final VenueRepository venueRepository;

    public Venue findVenue(UUID venueId) {
        return venueRepository.findById(venueId).orElseThrow(() -> new BusinessException(ErrorCode.VENUE_001));
    }

    /** Locks the venue row before image writes, then reads the fresh committed state. */
    public Venue lockForImageWrite(UUID venueId) {
        if (venueRepository.lockRow(venueId) == 0) {
            throw new BusinessException(ErrorCode.VENUE_001);
        }
        return findVenue(venueId);
    }

    /** Venue must exist and be active for every caller. */
    public Venue activeVenue(UUID venueId) {
        Venue venue = findVenue(venueId);
        if (venue.getStatus() == Venue.Status.INACTIVE) {
            throw new BusinessException(ErrorCode.VENUE_002);
        }
        return venue;
    }

    /** Inactive venues remain writable to Admin only. */
    public void requireWritable(Venue venue, CurrentActor actor) {
        if (venue.getStatus() == Venue.Status.INACTIVE && !actor.admin()) {
            throw new BusinessException(ErrorCode.VENUE_002);
        }
    }

    public void requireOwnerOrAdmin(Venue venue, CurrentActor actor) {
        if (!actor.admin() && !actor.userId().equals(venue.getCreatedByUserId())) {
            throw new BusinessException(ErrorCode.VENUE_013);
        }
    }
}
