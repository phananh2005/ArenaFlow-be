package com.phananh.arenaflow.application.service.impl;

import com.phananh.arenaflow.application.service.VenueSelectionService;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.projection.SelectableVenueView;
import com.phananh.arenaflow.domain.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Kiểm tra địa điểm được chọn còn tồn tại và ACTIVE, ghi thời điểm được chọn gần nhất. */
@Service
@RequiredArgsConstructor
public class VenueSelectionServiceImpl implements VenueSelectionService {

    private final VenueRepository venueRepository;

    @Override
    @Transactional(readOnly = true)
    public List<SelectableVenueView> requireSelectable(Collection<UUID> venueIds) {
        if (venueIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, SelectableVenueView> byId = venueRepository.findSelectableByIdIn(venueIds).stream()
                .collect(Collectors.toMap(SelectableVenueView::getId, Function.identity()));
        if (!byId.keySet().containsAll(venueIds)) {
            throw new BusinessException(ErrorCode.VENUE_001);
        }
        if (byId.values().stream().anyMatch(view -> !"ACTIVE".equals(view.getStatus()))) {
            throw new BusinessException(ErrorCode.VENUE_002);
        }
        return venueIds.stream().map(byId::get).toList();
    }

    /**
     * Ghi {@code last_selected_at} (không lùi giá trị đã có) cho các địa điểm; danh sách rỗng → không chạy SQL.
     * Bắt buộc chạy trong transaction của caller.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void markSelected(Collection<UUID> venueIds, LocalDateTime selectedAt) {
        if (!venueIds.isEmpty()) {
            venueRepository.markSelected(venueIds, selectedAt);
        }
    }
}
