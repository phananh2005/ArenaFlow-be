package com.phananh.arenaflow.application.mapper;

import com.phananh.arenaflow.application.dto.response.CostCustomItemResponse;
import com.phananh.arenaflow.application.dto.response.PairingConfirmationResponse;
import com.phananh.arenaflow.application.dto.response.PairingCostAgreementResponse;
import com.phananh.arenaflow.application.dto.response.PairingCostUpdateResponse;
import com.phananh.arenaflow.application.dto.response.PairingResponse;
import com.phananh.arenaflow.application.dto.response.PairingSummaryResponse;
import com.phananh.arenaflow.application.dto.response.PairingTeamResponse;
import com.phananh.arenaflow.application.dto.response.ProposalResponse;
import com.phananh.arenaflow.application.dto.response.ProposalUpdateResponse;
import com.phananh.arenaflow.domain.entity.match.CostCustomItem;
import com.phananh.arenaflow.domain.entity.match.Match;
import com.phananh.arenaflow.domain.entity.match.Pairing;
import com.phananh.arenaflow.domain.projection.MatchAvailabilityView;
import com.phananh.arenaflow.domain.projection.PairingDetailView;
import com.phananh.arenaflow.domain.projection.PairingSummaryView;
import com.phananh.arenaflow.domain.projection.ProposalCandidateView;
import com.phananh.arenaflow.domain.projection.TeamCaptainContextView;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response của thương lượng. Tên đội trưởng che qua {@link MapperSupport#maskName}; thời gian là giờ Việt Nam gán
 * thẳng. Response sau khi ghi dựng từ dữ liệu đã nạp trong cùng lần thử (không đọc lại).
 */
@Mapper(componentModel = "spring", uses = MapperSupport.class, imports = MapperSupport.class,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PairingMapper {

    @Mapping(target = "pairingId", source = "view.pairingId")
    @Mapping(target = "matchId", source = "view.matchId")
    @Mapping(target = "conversationId", source = "view.conversationId")
    @Mapping(target = "status", source = "view.effectiveStatus")
    @Mapping(target = "revision", source = "view.revision")
    @Mapping(target = "matchExpiresAt", source = "view.matchExpiresAt")
    @Mapping(target = "authorTeam", expression = "java(authorTeam(view))")
    @Mapping(target = "interestedTeam", expression = "java(interestedTeam(view))")
    @Mapping(target = "proposal", expression = "java(proposal(view))")
    @Mapping(target = "authorConfirmedAt", source = "view.authorConfirmedAt")
    @Mapping(target = "interestedConfirmedAt", source = "view.interestedConfirmedAt")
    @Mapping(target = "confirmedAt", source = "view.finalConfirmedAt")
    @Mapping(target = "costAgreement", expression = "java(costAgreement(view, items))")
    @Mapping(target = "createdAt", source = "view.createdAt")
    @Mapping(target = "updatedAt", source = "view.updatedAt")
    PairingResponse toResponse(PairingDetailView view, List<CostCustomItem> items);

    @Mapping(target = "status", source = "effectiveStatus")
    @Mapping(target = "proposalVenueId", source = "proposedVenueId")
    @Mapping(target = "proposalTimeOptionId", source = "proposedTimeOptionId")
    @Mapping(target = "confirmedAt", source = "finalConfirmedAt")
    @Mapping(target = "matchStartsAt", source = "matchStartTime")
    PairingSummaryResponse toSummary(PairingSummaryView view);

    @Mapping(target = "itemId", source = "id")
    CostCustomItemResponse toItem(CostCustomItem item);

    List<CostCustomItemResponse> toItems(List<CostCustomItem> items);

    /**
     * Response nhận kèo dựng từ pairing vừa ghi, kèo, ngữ cảnh đội trưởng của đội nhận kèo (người gọi) và dữ liệu đội
     * tác giả đã đọc; {@code conversationId} = cuộc trò chuyện đã có hoặc vừa tạo.
     */
    default PairingResponse toCreatedResponse(Pairing pairing, Match match, TeamCaptainContextView interestedTeam,
                                              MatchAvailabilityView availability, UUID conversationId) {
        PairingTeamResponse author = new PairingTeamResponse(match.getAuthorTeamId(), availability.getAuthorTeamName(),
                availability.getAuthorTeamAvatarUrl(), availability.getAuthorTeamReliabilityScore(),
                MapperSupport.maskName(availability.getAuthorCaptainName(), availability.getAuthorCaptainDeleted()));
        PairingTeamResponse interested = new PairingTeamResponse(pairing.getInterestedTeamId(), interestedTeam.getName(),
                interestedTeam.getAvatarUrl(), interestedTeam.getReliabilityScore(),
                MapperSupport.maskName(interestedTeam.getUserDisplayName(), interestedTeam.getUserDeleted()));
        PairingCostAgreementResponse emptyCost = new PairingCostAgreementResponse(null, null, null, null, null, null,
                List.of());
        return new PairingResponse(pairing.getId(), pairing.getMatchId(), conversationId, pairing.getStatus().name(),
                pairing.getRevision(), match.getExpiresAt(), author, interested, null, null, null, null, emptyCost,
                pairing.getCreatedAt(), pairing.getUpdatedAt());
    }

    default ProposalUpdateResponse toProposalUpdate(UUID pairingId, int revision, ProposalCandidateView candidate,
                                                    LocalDateTime authorConfirmedAt,
                                                    LocalDateTime interestedConfirmedAt, LocalDateTime updatedAt) {
        ProposalResponse proposal = new ProposalResponse(candidate.getVenueId(), candidate.getVenueName(),
                candidate.getAddress(), candidate.getVenueStatus(), candidate.getTimeOptionId(),
                candidate.getStartsAt(), candidate.getEndsAt());
        return new ProposalUpdateResponse(pairingId, revision, proposal, authorConfirmedAt, interestedConfirmedAt,
                updatedAt);
    }

    /** Sau khi lưu chi phí, xác nhận của cả hai bên luôn bị xoá. */
    default PairingCostUpdateResponse toCostUpdate(UUID pairingId, int revision, String costType, String costSplit,
                                                   Long venueAmount, Long drinksAmount, Long depositAmount,
                                                   String depositHolder, List<CostCustomItem> items,
                                                   LocalDateTime updatedAt) {
        PairingCostAgreementResponse cost = new PairingCostAgreementResponse(costType, costSplit, venueAmount,
                drinksAmount, depositAmount, depositHolder, toItems(items));
        return new PairingCostUpdateResponse(pairingId, revision, cost, null, null, updatedAt);
    }

    default PairingConfirmationResponse toConfirmation(UUID pairingId, Pairing.Status status, int revision,
                                                       LocalDateTime authorConfirmedAt,
                                                       LocalDateTime interestedConfirmedAt, LocalDateTime confirmedAt,
                                                       UUID matchId, Match.Status matchStatus) {
        return new PairingConfirmationResponse(pairingId, status.name(), revision, authorConfirmedAt,
                interestedConfirmedAt, confirmedAt, matchId, matchStatus.name());
    }

    default PairingTeamResponse authorTeam(PairingDetailView view) {
        return new PairingTeamResponse(view.getAuthorTeamId(), view.getAuthorTeamName(), view.getAuthorTeamAvatarUrl(),
                view.getAuthorTeamReliabilityScore(),
                MapperSupport.maskName(view.getAuthorCaptainName(), view.getAuthorCaptainDeleted()));
    }

    default PairingTeamResponse interestedTeam(PairingDetailView view) {
        return new PairingTeamResponse(view.getInterestedTeamId(), view.getInterestedTeamName(),
                view.getInterestedTeamAvatarUrl(), view.getInterestedTeamReliabilityScore(),
                MapperSupport.maskName(view.getInterestedCaptainName(), view.getInterestedCaptainDeleted()));
    }

    /** {@code null} khi chưa chốt phương án. */
    default ProposalResponse proposal(PairingDetailView view) {
        if (view.getProposedVenueId() == null) {
            return null;
        }
        return new ProposalResponse(view.getProposedVenueId(), view.getProposedVenueName(),
                view.getProposedVenueAddress(), view.getProposedVenueStatus(), view.getProposedTimeOptionId(),
                view.getProposedStartTime(), view.getProposedEndTime());
    }

    default PairingCostAgreementResponse costAgreement(PairingDetailView view, List<CostCustomItem> items) {
        return new PairingCostAgreementResponse(view.getCostType(), view.getCostSplit(), view.getCostVenueAmount(),
                view.getCostDrinksAmount(), view.getCostDepositAmount(), view.getCostDepositHolder(), toItems(items));
    }
}
