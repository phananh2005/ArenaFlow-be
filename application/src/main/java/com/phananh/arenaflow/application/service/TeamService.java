package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.CreateTeamRequest;
import com.phananh.arenaflow.application.dto.request.TransferCaptainRequest;
import com.phananh.arenaflow.application.dto.request.UpdateTeamRequest;
import com.phananh.arenaflow.application.dto.response.CaptainHistoryResponse;
import com.phananh.arenaflow.application.dto.response.CaptainTransferResponse;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.dto.response.TeamResponse;
import com.phananh.arenaflow.application.dto.response.TeamSummaryResponse;

import java.util.UUID;

public interface TeamService {

    TeamResponse createTeam(UUID userId, CreateTeamRequest request);

    TeamResponse getTeam(UUID teamId);

    PageResponse<TeamSummaryResponse> listTeams(TeamStatusFilter status, int page, int pageSize);

    TeamResponse updateTeam(UUID teamId, UUID userId, UpdateTeamRequest request);

    CaptainTransferResponse transferCaptain(UUID teamId, UUID userId, TransferCaptainRequest request);

    PageResponse<CaptainHistoryResponse> listCaptainHistory(UUID teamId, int page, int pageSize);
}
