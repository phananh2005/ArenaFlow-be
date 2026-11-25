package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.entity.match.Match;
import com.phananh.arenaflow.domain.projection.MatchAuthorAccessView;
import com.phananh.arenaflow.domain.projection.MatchAvailabilityView;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

import java.util.Optional;
import java.util.UUID;

class MatchRepositoryImpl implements MatchRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public void forceIncrementVersion(Match match) {
        entityManager.lock(match, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
    }

    @Override
    public MatchAvailabilityView findAvailability(UUID matchId, UUID teamId) {
        throw new UnsupportedOperationException("Bản tạm — câu truy vấn thuộc kèo luồng A");
    }

    @Override
    public Optional<MatchAuthorAccessView> findAuthorAccess(UUID matchId, UUID userId) {
        throw new UnsupportedOperationException("Bản tạm — câu truy vấn thuộc kèo luồng A");
    }
}
