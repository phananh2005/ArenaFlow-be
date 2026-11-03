package com.phananh.ArenaFlow.service;

import com.phananh.ArenaFlow.dto.request.CreateTeamRequest;
import com.phananh.ArenaFlow.dto.request.TransferCaptainRequest;
import com.phananh.ArenaFlow.dto.request.UpdateTeamRequest;
import com.phananh.ArenaFlow.dto.response.CaptainHistoryResponse;
import com.phananh.ArenaFlow.dto.response.CaptainTransferResponse;
import com.phananh.ArenaFlow.dto.response.PageResponse;
import com.phananh.ArenaFlow.dto.response.TeamResponse;
import com.phananh.ArenaFlow.dto.response.TeamSummaryResponse;

import java.util.UUID;

public interface TeamService {

    TeamResponse createTeam(UUID userId, CreateTeamRequest request);

    TeamResponse getTeam(UUID teamId);

    PageResponse<TeamSummaryResponse> listTeams(TeamStatusFilter status, int page, int pageSize);

    TeamResponse updateTeam(UUID teamId, UUID userId, UpdateTeamRequest request);

    CaptainTransferResponse transferCaptain(UUID teamId, UUID userId, TransferCaptainRequest request);

    PageResponse<CaptainHistoryResponse> listCaptainHistory(UUID teamId, int page, int pageSize);
}
