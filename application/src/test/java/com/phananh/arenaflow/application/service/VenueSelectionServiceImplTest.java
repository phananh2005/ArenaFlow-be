package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.service.impl.VenueSelectionServiceImpl;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.projection.SelectableVenueView;
import com.phananh.arenaflow.domain.repository.VenueRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Chọn địa điểm: kiểm tồn tại và ACTIVE theo thứ tự lỗi, giữ thứ tự yêu cầu, đầu vào rỗng không chạy SQL. */
class VenueSelectionServiceImplTest {

    private final VenueRepository venueRepository = mock(VenueRepository.class);
    private final SpelAwareProxyProjectionFactory projections = new SpelAwareProxyProjectionFactory();
    private final VenueSelectionService service = new VenueSelectionServiceImpl(venueRepository);

    @Test
    void requireSelectableReturnsViewsInRequestedOrderFromOneQuery() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(venueRepository.findSelectableByIdIn(anyCollection()))
                .thenReturn(List.of(view(second, "ACTIVE"), view(first, "ACTIVE")));

        List<SelectableVenueView> result = service.requireSelectable(List.of(first, second));

        assertThat(result).extracting(SelectableVenueView::getId).containsExactly(first, second);
    }

    @Test
    void requireSelectableReturnsVenue001WhenAnyIdIsMissingBeforeCheckingStatus() {
        UUID present = UUID.randomUUID();
        when(venueRepository.findSelectableByIdIn(anyCollection())).thenReturn(List.of(view(present, "INACTIVE")));

        assertError(() -> service.requireSelectable(List.of(present, UUID.randomUUID())), ErrorCode.VENUE_001);
    }

    @Test
    void requireSelectableReturnsVenue002WhenAnyVenueIsInactive() {
        UUID active = UUID.randomUUID();
        UUID inactive = UUID.randomUUID();
        when(venueRepository.findSelectableByIdIn(anyCollection()))
                .thenReturn(List.of(view(active, "ACTIVE"), view(inactive, "INACTIVE")));

        assertError(() -> service.requireSelectable(List.of(active, inactive)), ErrorCode.VENUE_002);
    }

    @Test
    void emptyInputsRunNoQuery() {
        assertThat(service.requireSelectable(List.of())).isEmpty();
        service.markSelected(List.of(), LocalDateTime.of(2026, 11, 20, 19, 0));

        verifyNoInteractions(venueRepository);
    }

    @Test
    void markSelectedDelegatesToSingleBulkUpdate() {
        UUID venueId = UUID.randomUUID();
        LocalDateTime selectedAt = LocalDateTime.of(2026, 11, 19, 7, 0);

        service.markSelected(List.of(venueId), selectedAt);

        verify(venueRepository).markSelected(List.of(venueId), selectedAt);
    }

    private SelectableVenueView view(UUID id, String status) {
        return projections.createProjection(SelectableVenueView.class, Map.of("id", id, "name", "Venue " + id,
                "address", "Address", "status", status));
    }

    private static void assertError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
