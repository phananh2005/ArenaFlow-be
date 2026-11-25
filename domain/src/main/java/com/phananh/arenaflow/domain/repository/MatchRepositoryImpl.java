package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.entity.match.Match;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

class MatchRepositoryImpl implements MatchRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public void forceIncrementVersion(Match match) {
        entityManager.lock(match, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
    }
}
