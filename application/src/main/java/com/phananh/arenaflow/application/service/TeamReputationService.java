package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.response.TeamReputationResponse;

import java.util.UUID;

public interface TeamReputationService {

    /** Uy tín đội ở mọi trạng thái, tính khi đọc trong một truy vấn; TEAM_001 khi không có đội. */
    TeamReputationResponse getTeamReputation(UUID teamId);
}
