package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.domain.projection.SelectableVenueView;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Cổng để luồng kèo và trận ngoài hệ thống chọn địa điểm. */
public interface VenueSelectionService {

    /** Một truy vấn; thiếu id → VENUE_001, có địa điểm INACTIVE → VENUE_002; kết quả theo thứ tự {@code venueIds}. */
    List<SelectableVenueView> requireSelectable(Collection<UUID> venueIds);

    /** Phải tham gia transaction của caller để last_selected_at rollback cùng kèo. */
    void markSelected(Collection<UUID> venueIds, OffsetDateTime selectedAt);
}
