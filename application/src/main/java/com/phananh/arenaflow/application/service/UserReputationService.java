package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.dto.response.TeamMercenaryRatingResponse;
import com.phananh.arenaflow.application.dto.response.UserReputationResponse;

import java.util.UUID;

/** Uy tín cá nhân công khai và đánh giá mercenary dành cho đội. */
public interface UserReputationService {

    UserReputationResponse getUserReputation(UUID userId);

    PageResponse<TeamMercenaryRatingResponse> listTeamMercenaryRatings(UUID teamId, int page, int pageSize);
}
