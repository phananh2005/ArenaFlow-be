package com.phananh.arenaflow.api.team;

import com.phananh.arenaflow.application.dto.response.ApiResponse;
import com.phananh.arenaflow.application.dto.response.InvitationCandidatesResponse;
import com.phananh.arenaflow.application.service.TeamMembershipService;
import com.phananh.arenaflow.application.service.TeamService;
import com.phananh.arenaflow.application.support.InvitationSearchRateLimiter;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.common.exception.RateLimitExceededException;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Giới hạn số lần tìm người để mời chạy ở controller, trước khi vào service (ngoài transaction). Không dựng Spring. */
class TeamControllerInvitationSearchTest {

    private final TeamService teamService = mock(TeamService.class);
    private final TeamMembershipService membershipService = mock(TeamMembershipService.class);
    private final InvitationSearchRateLimiter rateLimiter = mock(InvitationSearchRateLimiter.class);
    private final TeamController controller = new TeamController(teamService, membershipService, rateLimiter);

    private final UUID teamId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();

    @Test
    void acquiresRateLimitBeforeCallingService() {
        InvitationCandidatesResponse result = new InvitationCandidatesResponse(List.of(), false);
        when(membershipService.searchInvitationCandidates(teamId, callerId, "an")).thenReturn(result);

        ResponseEntity<ApiResponse<InvitationCandidatesResponse>> response =
                controller.searchInvitationCandidates(teamId, callerId, "an");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(ApiResponse.of(result));
        InOrder order = inOrder(rateLimiter, membershipService);
        order.verify(rateLimiter).acquire(callerId);
        order.verify(membershipService).searchInvitationCandidates(teamId, callerId, "an");
    }

    @Test
    void rejectedCallNeverReachesService() {
        doThrow(new RateLimitExceededException(ErrorCode.USER_007, 12)).when(rateLimiter).acquire(callerId);

        assertThatThrownBy(() -> controller.searchInvitationCandidates(teamId, callerId, "an"))
                .isInstanceOf(RateLimitExceededException.class);

        verifyNoInteractions(membershipService, teamService);
    }
}
