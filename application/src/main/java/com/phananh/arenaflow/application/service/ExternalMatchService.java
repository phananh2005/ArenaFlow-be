package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.CreateExternalMatchRequest;
import com.phananh.arenaflow.application.dto.request.UpdateExternalMatchRequest;
import com.phananh.arenaflow.application.dto.response.ExternalMatchCancellationResponse;
import com.phananh.arenaflow.application.dto.response.ExternalMatchResponse;
import com.phananh.arenaflow.application.dto.response.PageResponse;

import java.util.UUID;

/** Trận ngoài hệ thống do đội trưởng khai báo để tuyển mercenary. */
public interface ExternalMatchService {

    ExternalMatchResponse createExternalMatch(UUID teamId, UUID callerId, CreateExternalMatchRequest request);

    PageResponse<ExternalMatchResponse> listTeamExternalMatches(UUID teamId, UUID callerId,
                                                                ExternalMatchStatusFilter status, int page,
                                                                int pageSize);

    ExternalMatchResponse getExternalMatch(UUID externalMatchId, UUID callerId);

    ExternalMatchResponse updateExternalMatch(UUID externalMatchId, UUID callerId, UpdateExternalMatchRequest request);

    ExternalMatchCancellationResponse cancelExternalMatch(UUID externalMatchId, UUID callerId);
}
