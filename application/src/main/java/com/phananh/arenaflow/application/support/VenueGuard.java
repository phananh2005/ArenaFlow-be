package com.phananh.arenaflow.application.support;

import com.phananh.arenaflow.application.support.security.CurrentActor;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.entity.venue.Venue;
import com.phananh.arenaflow.domain.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Nguồn duy nhất cho các guard VENUE: tồn tại, trạng thái được phép ghi và quyền sở hữu. */
@Component
@RequiredArgsConstructor
public class VenueGuard {

    private final VenueRepository venueRepository;

    public Venue findVenue(UUID venueId) {
        return venueRepository.findById(venueId).orElseThrow(() -> new BusinessException(ErrorCode.VENUE_001));
    }

    /** Khoá dòng venue trước khi ghi ảnh, rồi đọc trạng thái mới nhất đã commit. */
    public Venue lockForImageWrite(UUID venueId) {
        if (venueRepository.lockRow(venueId) == 0) {
            throw new BusinessException(ErrorCode.VENUE_001);
        }
        return findVenue(venueId);
    }

    /** Địa điểm phải tồn tại và đang hoạt động với mọi caller. */
    public Venue activeVenue(UUID venueId) {
        Venue venue = findVenue(venueId);
        if (venue.getStatus() == Venue.Status.INACTIVE) {
            throw new BusinessException(ErrorCode.VENUE_002);
        }
        return venue;
    }

    /** Địa điểm ngừng hoạt động chỉ còn Admin được ghi. */
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
