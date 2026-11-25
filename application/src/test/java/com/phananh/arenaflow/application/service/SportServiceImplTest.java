package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.UpdateSportRequest;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.dto.response.SportResponse;
import com.phananh.arenaflow.application.mapper.SportMapper;
import com.phananh.arenaflow.application.service.impl.SportServiceImpl;
import com.phananh.arenaflow.application.support.AdminAuditLog;
import com.phananh.arenaflow.application.support.PageRequestFactory;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.entity.match.Sport;
import com.phananh.arenaflow.domain.repository.SportRepository;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SportServiceImplTest {

    private final SportRepository sportRepository = mock(SportRepository.class);
    private final AdminAuditLog auditLog = mock(AdminAuditLog.class);
    private final SportMapper mapper = Mappers.getMapper(SportMapper.class);
    private final SportServiceImpl service = new SportServiceImpl(sportRepository, mapper, new PageRequestFactory(), auditLog);
    private final UUID sportId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();

    private Sport sport(boolean active) {
        return Sport.builder().id(sportId).name("Bóng đá").isActive(active).build();
    }

    @Test
    void listActiveSportsReadsOnlyActiveSortedByName() {
        when(sportRepository.findByIsActiveOrderByNameAscIdAsc(true, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(sport(true)), PageRequest.of(0, 20), 1));

        PageResponse<SportResponse> page = service.listActiveSports(1, 20);

        assertThat(page.data()).extracting(SportResponse::sportName).containsExactly("Bóng đá");
        assertThat(page.pagination().totalItems()).isEqualTo(1);
    }

    @Test
    void adminListWithoutFilterReadsAll() {
        when(sportRepository.findAllByOrderByNameAscIdAsc(PageRequest.of(1, 10)))
                .thenReturn(new PageImpl<>(List.of(sport(false)), PageRequest.of(1, 10), 11));

        PageResponse<SportResponse> page = service.listSports(null, 2, 10);

        assertThat(page.data()).singleElement().extracting(SportResponse::isActive).isEqualTo(false);
    }

    @Test
    void adminListWithFilterReadsByFlag() {
        when(sportRepository.findByIsActiveOrderByNameAscIdAsc(false, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(sport(false)), PageRequest.of(0, 20), 1));

        assertThat(service.listSports(false, 1, 20).data()).hasSize(1);
    }

    @Test
    void updateMissingSportIsSport001() {
        when(sportRepository.findById(sportId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateSport(sportId, adminId, new UpdateSportRequest(false)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SPORT_001));
    }

    @Test
    void updateChangesFlagAndAuditsAfterCommit() {
        Sport sport = sport(true);
        when(sportRepository.findById(sportId)).thenReturn(Optional.of(sport));

        SportResponse response = service.updateSport(sportId, adminId, new UpdateSportRequest(false));

        assertThat(sport.getIsActive()).isFalse();
        assertThat(response.isActive()).isFalse();
        verify(auditLog).afterCommit(AdminAuditLog.SET_SPORT_ACTIVE, adminId, "SPORT", sportId);
    }

    @Test
    void updateWithSameValueWritesNothingAndDoesNotAudit() {
        when(sportRepository.findById(sportId)).thenReturn(Optional.of(sport(true)));

        SportResponse response = service.updateSport(sportId, adminId, new UpdateSportRequest(true));

        assertThat(response.isActive()).isTrue();
        verify(auditLog, never()).afterCommit(anyString(), any(), anyString(), any());
    }
}
