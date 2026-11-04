package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.MarkInactiveRequest;
import com.phananh.arenaflow.application.dto.response.AdminTeamSummaryResponse;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.dto.response.TeamResponse;

import java.util.UUID;

/** Quyền ADMIN chặn ở SecurityConfig (`/api/v1/admin/**`). */
public interface AdminTeamService {

    TeamResponse markInactive(UUID teamId, MarkInactiveRequest request);

    TeamResponse reactivate(UUID teamId);

    PageResponse<AdminTeamSummaryResponse> listTeams(TeamStatusFilter status, int page, int pageSize);
}
