package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.CreateTeamRequest;
import com.phananh.arenaflow.application.dto.request.DisbandTeamRequest;
import com.phananh.arenaflow.application.dto.request.TransferCaptainRequest;
import com.phananh.arenaflow.application.dto.request.UpdateTeamRequest;
import com.phananh.arenaflow.application.dto.response.CaptainHistoryResponse;
import com.phananh.arenaflow.application.dto.response.CaptainTransferResponse;
import com.phananh.arenaflow.application.dto.response.MyTeamResponse;
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

    /** {@code request} null khi client không gửi body. */
    TeamResponse disbandTeam(UUID teamId, UUID captainUserId, DisbandTeamRequest request);

    /**
     * Đội người gọi đang là thành viên ACTIVE; {@code role} null = mọi vai trò, CAPTAIN = đúng tập đội đang chặn xoá
     * tài khoản. Người gọi đã xoá tài khoản → USER_001.
     */
    PageResponse<MyTeamResponse> listMyTeams(UUID userId, MyTeamRoleFilter role, int page, int pageSize);
}
