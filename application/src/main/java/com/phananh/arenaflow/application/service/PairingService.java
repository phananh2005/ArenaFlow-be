package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.ConfirmPairingRequest;
import com.phananh.arenaflow.application.dto.request.CreatePairingRequest;
import com.phananh.arenaflow.application.dto.request.UpdatePairingCostAgreementRequest;
import com.phananh.arenaflow.application.dto.request.UpdateProposalRequest;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.dto.response.PairingConfirmationResponse;
import com.phananh.arenaflow.application.dto.response.PairingCostUpdateResponse;
import com.phananh.arenaflow.application.dto.response.PairingResponse;
import com.phananh.arenaflow.application.dto.response.PairingSummaryResponse;
import com.phananh.arenaflow.application.dto.response.ProposalUpdateResponse;

import java.util.UUID;

/** Thương lượng ghép kèo luồng B: nhận kèo, chốt phương án, chi phí + cọc, xác nhận / ghép kèo. */
public interface PairingService {

    PairingResponse createPairing(UUID matchId, UUID callerId, CreatePairingRequest request);

    PageResponse<PairingSummaryResponse> listMatchPairings(UUID matchId, UUID callerId, PairingStatusFilter status,
                                                           int page, int pageSize);

    PageResponse<PairingSummaryResponse> listTeamPairings(UUID teamId, UUID callerId, PairingRoleFilter role,
                                                          PairingStatusFilter status, int page, int pageSize);

    PairingResponse getPairing(UUID pairingId, UUID callerId);

    ProposalUpdateResponse updateProposal(UUID pairingId, UUID callerId, UpdateProposalRequest request);

    PairingCostUpdateResponse updateCostAgreement(UUID pairingId, UUID callerId,
                                                  UpdatePairingCostAgreementRequest request);

    PairingConfirmationResponse confirm(UUID pairingId, UUID callerId, ConfirmPairingRequest request);
}
