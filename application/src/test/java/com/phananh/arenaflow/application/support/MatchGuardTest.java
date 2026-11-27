package com.phananh.arenaflow.application.support;

import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.entity.match.Match;
import com.phananh.arenaflow.domain.repository.MatchRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MatchGuardTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 21, 10, 0);

    private final MatchRepository matchRepository = mock(MatchRepository.class);
    private final MatchGuard guard = new MatchGuard(matchRepository);

    private static void assertError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }

    @Test
    void findMatchReturnsMatch001WhenMissing() {
        UUID id = UUID.randomUUID();
        when(matchRepository.findById(id)).thenReturn(Optional.empty());
        assertError(() -> guard.findMatch(id), ErrorCode.MATCH_001);

        Match match = Match.builder().id(id).build();
        when(matchRepository.findById(id)).thenReturn(Optional.of(match));
        assertThat(guard.findMatch(id)).isSameAs(match);
    }

    @Test
    void expiryIsDueOnlyForOpenMatchAtOrAfterExpiresAt() {
        assertThat(guard.isExpiryDue(Match.Status.OPEN, NOW, NOW)).isTrue();
        assertThat(guard.isExpiryDue(Match.Status.OPEN, NOW.minusSeconds(1), NOW)).isTrue();
        assertThat(guard.isExpiryDue(Match.Status.OPEN, NOW.plusSeconds(1), NOW)).isFalse();
        assertThat(guard.isExpiryDue(Match.Status.CONFIRMED, NOW.minusDays(1), NOW)).isFalse();
    }

    @Test
    void requireNotExpiredRejectsPersistedExpiredStatus() {
        assertError(() -> guard.requireNotExpired(Match.Status.EXPIRED), ErrorCode.MATCH_004);
        guard.requireNotExpired(Match.Status.CANCELLED);
    }

    @Test
    void requireOpenMapsEveryOtherStatus() {
        assertError(() -> guard.requireOpen(match(Match.Status.EXPIRED)), ErrorCode.MATCH_004);
        assertError(() -> guard.requireOpen(match(Match.Status.CONFIRMED)), ErrorCode.MATCH_003);
        assertError(() -> guard.requireOpen(match(Match.Status.CANCELLED)), ErrorCode.MATCH_005);
        assertError(() -> guard.requireOpen(match(Match.Status.ARCHIVED)), ErrorCode.MATCH_005);
        assertError(() -> guard.requireOpen(match(Match.Status.COMPLETED)), ErrorCode.MATCH_005);
        guard.requireOpen(match(Match.Status.OPEN));
    }

    private static Match match(Match.Status status) {
        return Match.builder().status(status).build();
    }
}
