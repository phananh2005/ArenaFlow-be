package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.response.UserReputationResponse;
import com.phananh.arenaflow.application.mapper.MercenaryReputationMapper;
import com.phananh.arenaflow.application.service.impl.UserReputationServiceImpl;
import com.phananh.arenaflow.application.support.PageRequestFactory;
import com.phananh.arenaflow.application.support.TeamGuard;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.projection.UserReputationView;
import com.phananh.arenaflow.domain.repository.RatingRepository;
import com.phananh.arenaflow.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserReputationServiceImplTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 12, 0);

    private final UserRepository userRepository = mock(UserRepository.class);
    private final SpelAwareProxyProjectionFactory projections = new SpelAwareProxyProjectionFactory();
    private final UUID userId = UUID.randomUUID();
    private final UserReputationServiceImpl service = new UserReputationServiceImpl(userRepository,
            mock(RatingRepository.class), mock(TeamGuard.class), Mappers.getMapper(MercenaryReputationMapper.class),
            new PageRequestFactory(), Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()));

    @Test
    void deletedOrMissingUserIsNotFound() {
        when(userRepository.findReputation(eq(userId), any(), anyInt())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getUserReputation(userId)).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_001));

        when(userRepository.findReputation(eq(userId), any(), anyInt())).thenReturn(Optional.of(view(Map.of(
                "deleted", true))));
        assertThatThrownBy(() -> service.getUserReputation(userId)).isInstanceOf(BusinessException.class);
    }

    @Test
    void newcomerHasNoCompletionRate() {
        when(userRepository.findReputation(eq(userId), any(), anyInt())).thenReturn(Optional.of(view(Map.of(
                "completedMatches", 3L, "countedMatches", 4L))));

        UserReputationResponse response = service.getUserReputation(userId);

        assertThat(response.completion().completionRate()).isNull();
        assertThat(response.completion().isNewcomer()).isTrue();
        assertThat(response.rating().averageRating()).isNull();
        assertThat(response.reliabilityLevel()).isEqualTo("NORMAL");
    }

    @Test
    void ratesAndLevelsAreRounded() {
        when(userRepository.findReputation(eq(userId), any(), anyInt())).thenReturn(Optional.of(view(Map.of(
                "completedMatches", 5L, "countedMatches", 6L, "averageRating", new BigDecimal("4.25"),
                "ratingCount", 4L, "reliabilityScore", 49, "verifiedAt", NOW))));

        UserReputationResponse response = service.getUserReputation(userId);

        assertThat(response.completion().completionRate()).isEqualTo(83);
        assertThat(response.rating().averageRating()).isEqualByComparingTo("4.3");
        assertThat(response.reliabilityLevel()).isEqualTo("CRITICAL");
        assertThat(response.verification().isVerified()).isTrue();
    }

    private UserReputationView view(Map<String, Object> overrides) {
        Map<String, Object> values = new HashMap<>();
        values.put("userId", userId);
        values.put("deleted", false);
        values.put("reliabilityScore", 80);
        values.put("completedMatches", 0L);
        values.put("countedMatches", 0L);
        values.put("ratingCount", 0L);
        values.putAll(overrides);
        return projections.createProjection(UserReputationView.class, values);
    }
}
