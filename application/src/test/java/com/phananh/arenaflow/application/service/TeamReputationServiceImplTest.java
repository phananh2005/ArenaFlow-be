package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.response.TeamReputationResponse;
import com.phananh.arenaflow.application.mapper.ReputationMapper;
import com.phananh.arenaflow.application.service.impl.TeamReputationServiceImpl;
import com.phananh.arenaflow.application.support.reputation.ReputationRules;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.projection.TeamReputationView;
import com.phananh.arenaflow.domain.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TeamReputationServiceImplTest {

    private final TeamRepository teamRepository = mock(TeamRepository.class);
    private final TeamReputationService service = new TeamReputationServiceImpl(teamRepository,
            Mappers.getMapper(ReputationMapper.class));
    private final UUID teamId = UUID.randomUUID();

    @Test
    void missingTeamIsTeam001() {
        when(teamRepository.findReputation(teamId, ReputationRules.LATE_CANCEL_WINDOW_HOURS)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTeamReputation(teamId)).isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode()).isEqualTo(ErrorCode.TEAM_001);
    }

    @Test
    void establishedTeamGetsRoundedRateAverageLevelAndBadge() {
        LocalDateTime verifiedAt = LocalDateTime.of(2026, 11, 10, 10, 0);
        stub(84, 6, 7, new BigDecimal("4.25"), 6, verifiedAt);

        TeamReputationResponse response = service.getTeamReputation(teamId);

        assertThat(response.reliabilityLevel()).isEqualTo("NORMAL");
        assertThat(response.completion().completionRate()).isEqualTo(86);
        assertThat(response.completion().isNewcomer()).isFalse();
        assertThat(response.rating().averageRating()).isEqualByComparingTo("4.3");
        assertThat(response.rating().ratingCount()).isEqualTo(6);
        assertThat(response.verification().isVerified()).isTrue();
        assertThat(response.verification().verifiedAt()).isEqualTo(verifiedAt);
    }

    @Test
    void newcomerHasNoRateAndTeamWithoutStarsHasNoAverage() {
        stub(96, 2, 4, null, 0, null);

        TeamReputationResponse response = service.getTeamReputation(teamId);

        assertThat(response.reliabilityLevel()).isEqualTo("EXCELLENT");
        assertThat(response.completion().completionRate()).isNull();
        assertThat(response.completion().isNewcomer()).isTrue();
        assertThat(response.completion().countedMatches()).isEqualTo(4);
        assertThat(response.rating().averageRating()).isNull();
        assertThat(response.verification().isVerified()).isFalse();
    }

    @Test
    void completionRateRoundsHalfUp() {
        stub(50, 1, 8, null, 0, null);

        assertThat(service.getTeamReputation(teamId).completion().completionRate()).isEqualTo(13);
    }

    private void stub(int score, long completed, long counted, BigDecimal average, long ratingCount,
                      LocalDateTime verifiedAt) {
        Map<String, Object> values = new HashMap<>();
        values.put("teamId", teamId);
        values.put("reliabilityScore", score);
        values.put("completedMatches", completed);
        values.put("countedMatches", counted);
        values.put("averageRating", average);
        values.put("ratingCount", ratingCount);
        values.put("verifiedAt", verifiedAt);
        when(teamRepository.findReputation(teamId, ReputationRules.LATE_CANCEL_WINDOW_HOURS)).thenReturn(Optional.of(
                new SpelAwareProxyProjectionFactory().createProjection(TeamReputationView.class, values)));
    }
}
