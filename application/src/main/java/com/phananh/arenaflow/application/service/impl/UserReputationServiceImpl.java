package com.phananh.arenaflow.application.service.impl;

import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.dto.response.TeamMercenaryRatingResponse;
import com.phananh.arenaflow.application.dto.response.UserReputationResponse;
import com.phananh.arenaflow.application.mapper.MercenaryReputationMapper;
import com.phananh.arenaflow.application.service.UserReputationService;
import com.phananh.arenaflow.application.support.PageRequestFactory;
import com.phananh.arenaflow.application.support.TeamGuard;
import com.phananh.arenaflow.application.support.reputation.ReputationRules;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.repository.RatingRepository;
import com.phananh.arenaflow.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserReputationServiceImpl implements UserReputationService {

    private final UserRepository userRepository;
    private final RatingRepository ratingRepository;
    private final TeamGuard teamGuard;
    private final MercenaryReputationMapper reputationMapper;
    private final PageRequestFactory pageRequestFactory;
    private final Clock clock;

    /** Uy tín cá nhân (1 truy vấn); không có / đã xoá tài khoản → USER_001. */
    @Override
    @Transactional(readOnly = true)
    public UserReputationResponse getUserReputation(UUID userId) {
        return userRepository.findReputation(userId, LocalDateTime.now(clock), ReputationRules.CANCEL_WINDOW_HOURS)
                .filter(view -> !Boolean.TRUE.equals(view.getDeleted()))
                .map(reputationMapper::toReputation)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_001));
    }

    /** Đánh giá mercenary dành cho đội (3 truy vấn): TEAM_001; đội ở mọi trạng thái. */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<TeamMercenaryRatingResponse> listTeamMercenaryRatings(UUID teamId, int page, int pageSize) {
        teamGuard.requireTeam(teamId);
        return PageResponse.from(ratingRepository.findMercenaryRatingsOfTeam(teamId,
                pageRequestFactory.create(page, pageSize)), reputationMapper::toTeamRating, page, pageSize);
    }
}
