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

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void markSelected(Collection<UUID> venueIds, OffsetDateTime selectedAt) {
        if (!venueIds.isEmpty()) {
            venueRepository.markSelected(venueIds, selectedAt);
        }
    }
}
