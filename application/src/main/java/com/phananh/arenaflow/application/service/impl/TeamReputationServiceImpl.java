package com.phananh.arenaflow.application.service.impl;

import com.phananh.arenaflow.application.dto.response.TeamReputationResponse;
import com.phananh.arenaflow.application.mapper.ReputationMapper;
import com.phananh.arenaflow.application.service.TeamReputationService;
import com.phananh.arenaflow.application.support.reputation.ReputationRules;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TeamReputationServiceImpl implements TeamReputationService {

    private final TeamRepository teamRepository;
    private final ReputationMapper reputationMapper;

    /** Q1 điểm + thống kê. */
    @Override
    @Transactional(readOnly = true)
    public TeamReputationResponse getTeamReputation(UUID teamId) {
        return teamRepository.findReputation(teamId, ReputationRules.LATE_CANCEL_WINDOW_HOURS)
                .map(reputationMapper::toReputation)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_001));
    }
}
