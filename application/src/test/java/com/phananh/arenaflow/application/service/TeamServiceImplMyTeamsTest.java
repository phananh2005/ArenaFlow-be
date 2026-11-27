package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.response.MyTeamResponse;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.mapper.TeamMapper;
import com.phananh.arenaflow.application.service.impl.TeamServiceImpl;
import com.phananh.arenaflow.application.support.PageRequestFactory;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.projection.MyTeamView;
import com.phananh.arenaflow.domain.repository.TeamMemberRepository;
import com.phananh.arenaflow.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Đội của tôi (service, không DB). */
@ExtendWith(MockitoExtension.class)
class TeamServiceImplMyTeamsTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Spy
    private TeamMapper teamMapper = Mappers.getMapper(TeamMapper.class);
    @Spy
    private PageRequestFactory pageRequestFactory = new PageRequestFactory();
    @InjectMocks
    private TeamServiceImpl service;

    private final SpelAwareProxyProjectionFactory projections = new SpelAwareProxyProjectionFactory();
    private final UUID userId = UUID.randomUUID();

    private MyTeamView view(String role) {
        Map<String, Object> values = new HashMap<>();
        values.put("teamId", UUID.randomUUID());
        values.put("teamName", "Team R");
        values.put("avatarUrl", null);
        values.put("skillLevels", "[]");
        values.put("status", "ACTIVE");
        values.put("captainName", "Me");
        values.put("captainDeleted", false);
        values.put("memberCount", 3L);
        values.put("role", role);
        values.put("joinedAt", LocalDateTime.of(2026, 10, 1, 10, 0));
        return projections.createProjection(MyTeamView.class, values);
    }

    @Test
    void captainFilterPassesRoleNameToQuery() {
        when(userRepository.existsByIdAndDeletedAtIsNull(userId)).thenReturn(true);
        when(teamMemberRepository.findMyTeams(userId, "CAPTAIN", PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(view("CAPTAIN")), PageRequest.of(0, 20), 1));

        PageResponse<MyTeamResponse> page = service.listMyTeams(userId, MyTeamRoleFilter.CAPTAIN, 1, 20);

        assertThat(page.data()).singleElement().satisfies(team -> {
            assertThat(team.role()).isEqualTo("CAPTAIN");
            assertThat(team.joinedAt()).isEqualTo(LocalDateTime.of(2026, 10, 1, 10, 0));
        });
        assertThat(page.pagination().totalItems()).isEqualTo(1);
    }

    @Test
    void noFilterPassesNullRole() {
        when(userRepository.existsByIdAndDeletedAtIsNull(userId)).thenReturn(true);
        when(teamMemberRepository.findMyTeams(userId, null, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        PageResponse<MyTeamResponse> page = service.listMyTeams(userId, null, 1, 20);

        assertThat(page.data()).isEmpty();
        verify(teamMemberRepository).findMyTeams(any(), isNull(), any());
    }

    @Test
    void deletedCallerFailsWithUser001() {
        when(userRepository.existsByIdAndDeletedAtIsNull(userId)).thenReturn(false);

        assertThatThrownBy(() -> service.listMyTeams(userId, null, 1, 20))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.USER_001);
        verify(teamMemberRepository, never()).findMyTeams(any(), any(), any());
    }
}
