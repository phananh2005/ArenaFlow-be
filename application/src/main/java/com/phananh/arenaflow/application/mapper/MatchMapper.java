package com.phananh.arenaflow.application.mapper;

import com.phananh.arenaflow.application.dto.response.CostAgreementResponse;
import com.phananh.arenaflow.application.dto.response.CostCustomItemResponse;
import com.phananh.arenaflow.application.dto.response.MatchCancellationResponse;
import com.phananh.arenaflow.application.dto.response.MatchCostAgreementResponse;
import com.phananh.arenaflow.application.dto.response.MatchRequestResponse;
import com.phananh.arenaflow.application.dto.response.MatchResponse;
import com.phananh.arenaflow.application.dto.response.MatchSummaryResponse;
import com.phananh.arenaflow.application.dto.response.MatchTeamResponse;
import com.phananh.arenaflow.application.dto.response.MatchTimeOptionResponse;
import com.phananh.arenaflow.application.dto.response.MatchVenueResponse;
import com.phananh.arenaflow.domain.entity.match.CostCustomItem;
import com.phananh.arenaflow.domain.entity.match.Match;
import com.phananh.arenaflow.domain.entity.match.MatchRequest;
import com.phananh.arenaflow.domain.entity.match.Sport;
import com.phananh.arenaflow.domain.projection.MatchDetailView;
import com.phananh.arenaflow.domain.projection.MatchOptionRowView;
import com.phananh.arenaflow.domain.projection.MatchRequestView;
import com.phananh.arenaflow.domain.projection.MatchSummaryView;
import com.phananh.arenaflow.domain.projection.SelectableVenueView;
import com.phananh.arenaflow.domain.projection.TeamCaptainContextView;
import com.phananh.arenaflow.domain.projection.TimeOptionView;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring", uses = MapperSupport.class, imports = MapperSupport.class,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface MatchMapper {

    /** Giá trị {@code kind} của dòng lựa chọn địa điểm. */
    String VENUE_OPTION = "VENUE";
    /** Giá trị {@code kind} của dòng khung giờ. */
    String TIME_OPTION = "TIME";

    /** Kèo luồng A (không có lựa chọn): như {@link #toResponse(MatchDetailView, List, List)} với lựa chọn rỗng. */
    default MatchResponse toResponse(MatchDetailView view, List<CostCustomItem> customItems) {
        return toResponse(view, customItems, List.of());
    }

    /**
     * Kèo chi tiết từ projection + khoản tự nhập + dòng lựa chọn đã nạp. Dòng lựa chọn tách theo {@code kind} và giữ
     * thứ tự đọc từ DB (địa điểm theo thứ tự nhập, khung giờ theo giờ bắt đầu); kèo luồng A: hai danh sách rỗng.
     * {@code opponentTeam}, {@code venue} null khi chưa có.
     */
    default MatchResponse toResponse(MatchDetailView view, List<CostCustomItem> customItems,
                                     List<MatchOptionRowView> optionRows) {
        MatchTeamResponse opponent = view.getOpponentTeamId() == null ? null
                : new MatchTeamResponse(view.getOpponentTeamId(), view.getOpponentTeamName(),
                view.getOpponentTeamAvatarUrl(), view.getOpponentTeamReliabilityScore());
        MatchVenueResponse venue = view.getVenueId() == null ? null
                : new MatchVenueResponse(view.getVenueId(), view.getVenueName(), view.getVenueAddress(),
                view.getVenueLatitude(), view.getVenueLongitude(), view.getVenueStatus(), view.getVenuePrimaryImageUrl());
        MatchCostAgreementResponse costAgreement = new MatchCostAgreementResponse(view.getCostType(), view.getCostSplit(),
                view.getCostVenueAmount(), view.getCostDrinksAmount(), view.getCostDepositAmount(),
                view.getCostDepositHolder(), toCustomItems(customItems));
        return new MatchResponse(view.getMatchId(), view.getType(), view.getEffectiveStatus(), view.getSportId(),
                view.getSportName(), view.getFormat(), MapperSupport.skillLevels(view.getSkillLevels()), view.getAgeNote(),
                view.getMatchNature(), view.getMatchStartTime(), view.getMatchEndTime(), view.getExpiresAt(),
                new MatchTeamResponse(view.getAuthorTeamId(), view.getAuthorTeamName(), view.getAuthorTeamAvatarUrl(),
                        view.getAuthorTeamReliabilityScore()),
                opponent, venue, toVenueOptions(optionRows), toTimeOptions(optionRows), view.getConfirmedPairingId(),
                costAgreement,
                view.getCancelledByTeamId(), view.getCancelledAt(), view.getCancelReason(), view.getCreatedAt(),
                view.getUpdatedAt());
    }

    /**
     * Kèo luồng B vừa tạo, dựng từ dữ liệu đã nạp khi kiểm quyền / bộ môn / địa điểm và id khung giờ trả về lúc ghi
     * (không đọc lại): {@code venue}, {@code opponentTeam} null; lựa chọn địa điểm theo thứ tự nhập, khung giờ theo giờ
     * bắt đầu; thoả thuận chi phí rỗng.
     */
    default MatchResponse toCreatedNeedVenue(Match match, TeamCaptainContextView author, Sport sport,
                                             List<SelectableVenueView> venueOptions, List<TimeOptionView> timeOptions) {
        List<MatchVenueResponse> venues = venueOptions.stream()
                .map(venue -> new MatchVenueResponse(venue.getId(), venue.getName(), venue.getAddress(),
                        venue.getLatitude(), venue.getLongitude(), venue.getStatus(), venue.getPrimaryImageUrl()))
                .toList();
        List<MatchTimeOptionResponse> times = timeOptions.stream()
                .sorted(Comparator.comparing(TimeOptionView::getProposedStartTime))
                .map(option -> new MatchTimeOptionResponse(option.getId(), option.getProposedStartTime(),
                        option.getProposedEndTime()))
                .toList();
        MatchCostAgreementResponse emptyAgreement = new MatchCostAgreementResponse(null, null, null, null, null, null,
                List.of());
        return new MatchResponse(match.getId(), match.getType().name(), match.getStatus().name(), match.getSportId(),
                sport.getName(), match.getFormat().name(), match.getSkillLevels(), match.getAgeNote(),
                match.getMatchNature(), match.getMatchStartTime(), match.getMatchEndTime(), match.getExpiresAt(),
                new MatchTeamResponse(match.getAuthorTeamId(), author.getName(), author.getAvatarUrl(),
                        author.getReliabilityScore()),
                null, null, venues, times, null, emptyAgreement, null, null, null, match.getCreatedAt(),
                match.getUpdatedAt());
    }

    /** Dòng {@code kind = VENUE} → lựa chọn địa điểm, giữ thứ tự. */
    private static List<MatchVenueResponse> toVenueOptions(List<MatchOptionRowView> optionRows) {
        return optionRows.stream()
                .filter(row -> VENUE_OPTION.equals(row.getKind()))
                .map(row -> new MatchVenueResponse(row.getVenueId(), row.getVenueName(), row.getAddress(),
                        row.getLatitude(), row.getLongitude(), row.getVenueStatus(), row.getPrimaryImageUrl()))
                .toList();
    }

    /** Dòng {@code kind = TIME} → khung giờ, giữ thứ tự. */
    private static List<MatchTimeOptionResponse> toTimeOptions(List<MatchOptionRowView> optionRows) {
        return optionRows.stream()
                .filter(row -> TIME_OPTION.equals(row.getKind()))
                .map(row -> new MatchTimeOptionResponse(row.getTimeOptionId(), row.getStartsAt(), row.getEndsAt()))
                .toList();
    }

    @Mapping(target = "itemId", source = "id")
    CostCustomItemResponse toCustomItem(CostCustomItem item);

    List<CostCustomItemResponse> toCustomItems(List<CostCustomItem> items);

    @Mapping(target = "status", source = "effectiveStatus")
    @Mapping(target = "startsAt", source = "matchStartTime")
    @Mapping(target = "endsAt", source = "matchEndTime")
    @Mapping(target = "skillLevels", expression = "java(MapperSupport.skillLevels(view.getSkillLevels()))")
    @Mapping(target = "distanceKm", expression = "java(roundDistance(view.getDistanceKm()))")
    MatchSummaryResponse toSummary(MatchSummaryView view);

    /** Thoả thuận vừa lưu dựng từ entity (đã flush, {@code updatedAt} là giá trị ghi DB) + khoản đã đọc lại. */
    default CostAgreementResponse toCostAgreement(Match match, List<CostCustomItem> customItems) {
        return new CostAgreementResponse(match.getId(), enumName(match.getCostType()), enumName(match.getCostSplit()),
                match.getCostVenueAmount(), match.getCostDrinksAmount(), toCustomItems(customItems), match.getUpdatedAt());
    }

    default MatchCancellationResponse toCancellation(Match match, int reliabilityPenalty) {
        return new MatchCancellationResponse(match.getId(), match.getStatus().name(), match.getCancelledByTeamId(),
                match.getCancelledAt(), match.getCancelReason(), reliabilityPenalty);
    }

    @Mapping(target = "status", source = "effectiveStatus")
    MatchRequestResponse toRequest(MatchRequestView view);

    /** Yêu cầu vừa tạo: tên/điểm đội gửi lấy từ dữ liệu đã đọc khi kiểm quyền, không đọc lại. */
    default MatchRequestResponse toCreatedRequest(MatchRequest request, TeamCaptainContextView requester,
                                                  UUID conversationId) {
        return new MatchRequestResponse(request.getId(), request.getMatchId(), conversationId,
                request.getRequesterTeamId(), requester.getName(), requester.getReliabilityScore(),
                request.getStatus().name(), request.getReason(), request.getRequestedAt(), request.getRespondedAt(),
                request.getWithdrawnAt(), null, null, null);
    }

    private static String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    /** Khoảng cách làm tròn 2 chữ số (HALF_UP); null khi không tìm theo toạ độ. */
    default BigDecimal roundDistance(Double distanceKm) {
        return distanceKm == null ? null : BigDecimal.valueOf(distanceKm).setScale(2, RoundingMode.HALF_UP);
    }
}
