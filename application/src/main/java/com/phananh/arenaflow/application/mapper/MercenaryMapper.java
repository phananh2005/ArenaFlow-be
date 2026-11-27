package com.phananh.arenaflow.application.mapper;

import com.phananh.arenaflow.application.dto.response.ApplicantBriefResponse;
import com.phananh.arenaflow.application.dto.response.ApplicantResponse;
import com.phananh.arenaflow.application.dto.response.ApplicationResponse;
import com.phananh.arenaflow.application.dto.response.AreaResponse;
import com.phananh.arenaflow.application.dto.response.ExternalMatchResponse;
import com.phananh.arenaflow.application.dto.response.MercenaryTeamBriefResponse;
import com.phananh.arenaflow.application.dto.response.RecruitmentMatchResponse;
import com.phananh.arenaflow.application.dto.response.RecruitmentResponse;
import com.phananh.arenaflow.application.dto.response.VenueRevealResponse;
import com.phananh.arenaflow.application.support.mercenary.MercenaryRules;
import com.phananh.arenaflow.domain.entity.mercenary.ExternalMatch;
import com.phananh.arenaflow.domain.entity.mercenary.MercenaryApplication;
import com.phananh.arenaflow.domain.projection.ApplicantRowView;
import com.phananh.arenaflow.domain.projection.ApplicationView;
import com.phananh.arenaflow.domain.projection.ApplyContextView;
import com.phananh.arenaflow.domain.projection.ExternalMatchView;
import com.phananh.arenaflow.domain.projection.RecruitContextView;
import com.phananh.arenaflow.domain.projection.RecruitmentRowView;
import com.phananh.arenaflow.domain.projection.RecruitmentView;
import com.phananh.arenaflow.domain.projection.SelectableVenueView;
import com.phananh.arenaflow.domain.projection.TeamCaptainContextView;
import org.mapstruct.Mapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Dựng response tuyển mercenary từ projection / entity đã nạp (không đọc lại DB).
 * <p>Bảo vệ vị trí sân: sân và tên đội trưởng chỉ trả khi người xem là đội trưởng đội tuyển hoặc có đơn APPROVED (trạng
 * thái hiệu lực) ở tin; khu vực "phường, tỉnh" luôn trả; khoảng cách làm tròn lên bội số 0,5 km.
 */
@Mapper(componentModel = "spring")
public interface MercenaryMapper {

    String SYSTEM = "SYSTEM";
    String EXTERNAL = "EXTERNAL";
    String CAPTAIN = "CAPTAIN";
    String APPLICANT = "APPLICANT";
    String APPROVED = "APPROVED";
    String PENDING = "PENDING";
    String REJECTED = "REJECTED";
    String OPEN = "OPEN";
    Set<String> TERMINAL_RECRUITMENT = Set.of("CLOSED", "EXPIRED");

    /** Tin cho mọi màn đọc; ẩn / hiện theo cờ người xem có trong dòng. */
    default RecruitmentResponse toResponse(RecruitmentView v) {
        return toResponse(v, v.getQuantityNeeded(), v.getEffectiveStatus(), v.getClosedAt(), v.getUpdatedAt(), null);
    }

    /** Tin sau khi sửa số lượng (dựng từ context + giá trị mới). */
    default RecruitmentResponse toUpdated(RecruitmentView v, int quantityNeeded, String status, LocalDateTime updatedAt) {
        return toResponse(v, quantityNeeded, status, v.getClosedAt(), updatedAt, null);
    }

    /** Tin sau khi đóng, kèm số đơn chờ vừa bị từ chối. */
    default RecruitmentResponse toClosed(RecruitmentView v, LocalDateTime closedAt, int rejectedCount) {
        return toResponse(v, v.getQuantityNeeded(), "CLOSED", closedAt, closedAt, rejectedCount);
    }

    private RecruitmentResponse toResponse(RecruitmentView v, int quantityNeeded, String status,
                                           LocalDateTime closedAt, LocalDateTime updatedAt, Integer rejectedCount) {
        boolean captain = Boolean.TRUE.equals(v.getViewerIsCaptain());
        String myStatus = effectiveApplicationStatus(v.getMyApplicationStatus(), status);
        boolean myActive = v.getMyApplicationId() != null && (PENDING.equals(myStatus) || APPROVED.equals(myStatus));
        boolean reveal = captain || (myActive && APPROVED.equals(myStatus));
        String viewerRole = captain ? CAPTAIN : myActive ? APPLICANT : null;
        Integer pending = captain ? (TERMINAL_RECRUITMENT.contains(status) ? 0 : toInt(v.getPendingCount())) : null;
        RecruitmentMatchResponse match = new RecruitmentMatchResponse(v.getSource(), v.getExternalMatchId(),
                v.getOpponentName(), v.getGameStart(), v.getGameEnd(), v.getFormat(),
                MapperSupport.skillLevels(v.getSkillLevels()), area(v.getWardName(), v.getProvinceName()),
                roundDistance(v.getDistanceKm()),
                reveal ? venue(v.getVenueId(), v.getVenueName(), v.getVenueAddress(), v.getVenueLatitude(),
                        v.getVenueLongitude()) : null);
        return new RecruitmentResponse(v.getRecruitmentId(), v.getMatchId(),
                new MercenaryTeamBriefResponse(v.getTeamId(), v.getTeamName(), v.getTeamAvatarUrl()), v.getPosition(),
                quantityNeeded, v.getQuantityApproved(), status, match,
                reveal ? MapperSupport.maskName(v.getCaptainName(), v.getCaptainDeleted()) : null, viewerRole,
                myActive ? new RecruitmentResponse.MyApplicationResponse(v.getMyApplicationId(), myStatus,
                        v.getMyConversationId()) : null,
                pending, rejectedCount, closedAt, v.getCreatedAt(), updatedAt);
    }

    /** Tin vừa tạo (người gọi là đội trưởng → hiện sân và tên mình). */
    default RecruitmentResponse toCreated(RecruitContextView ctx, RecruitmentRowView row) {
        RecruitmentMatchResponse match = new RecruitmentMatchResponse(ctx.getSource(), ctx.getExternalMatchId(),
                ctx.getOpponentName(), ctx.getGameStart(), ctx.getGameEnd(), ctx.getFormat(),
                MapperSupport.skillLevels(ctx.getSkillLevels()), area(ctx.getWardName(), ctx.getProvinceName()), null,
                venue(ctx.getVenueId(), ctx.getVenueName(), ctx.getVenueAddress(), ctx.getVenueLatitude(),
                        ctx.getVenueLongitude()));
        return new RecruitmentResponse(row.getId(), ctx.getMatchId(),
                new MercenaryTeamBriefResponse(ctx.getTeamId(), ctx.getTeamName(), ctx.getTeamAvatarUrl()),
                row.getPosition(), row.getQuantityNeeded(), 0, OPEN, match,
                MapperSupport.maskName(ctx.getCallerName(), ctx.getCallerDeleted()), CAPTAIN, null, 0, null, null,
                row.getCreatedAt(), row.getCreatedAt());
    }

    /** Tin vừa tạo cùng lúc khai báo trận ngoài (người gọi là đội trưởng). */
    default RecruitmentResponse toCreatedExternalRecruitment(ExternalMatch m, SelectableVenueView venue,
                                                             TeamCaptainContextView team, RecruitmentRowView row) {
        RecruitmentMatchResponse match = new RecruitmentMatchResponse(EXTERNAL, m.getId(), m.getOpponentName(),
                m.getMatchStartTime(), m.getMatchEndTime(), null, m.getSkillLevels(),
                area(venue.getWardName(), venue.getProvinceName()), null, venue(venue));
        return new RecruitmentResponse(row.getId(), null,
                new MercenaryTeamBriefResponse(m.getTeamId(), team.getName(), team.getAvatarUrl()), row.getPosition(),
                row.getQuantityNeeded(), 0, OPEN, match,
                MapperSupport.maskName(team.getUserDisplayName(), team.getUserDeleted()), CAPTAIN, null, 0, null, null,
                row.getCreatedAt(), row.getCreatedAt());
    }

    /** Trận ngoài (màn của đội trưởng — luôn có sân). */
    default ExternalMatchResponse toExternalMatch(ExternalMatchView v) {
        return new ExternalMatchResponse(v.getExternalMatchId(), v.getTeamId(), v.getStatus(), v.getStartsAt(),
                v.getEndsAt(), MapperSupport.skillLevels(v.getSkillLevels()), v.getOpponentName(),
                venue(v.getVenueId(), v.getVenueName(), v.getVenueAddress(), v.getVenueLatitude(),
                        v.getVenueLongitude()),
                area(v.getWardName(), v.getProvinceName()), Boolean.TRUE.equals(v.getHasApprovedMercenary()),
                v.getCancelledAt(), v.getCreatedAt(), v.getUpdatedAt(), null);
    }

    /** Trận ngoài vừa khai báo + tin vừa tạo. */
    default ExternalMatchResponse toCreatedExternalMatch(ExternalMatch m, SelectableVenueView venue,
                                                         List<RecruitmentResponse> recruitments) {
        return new ExternalMatchResponse(m.getId(), m.getTeamId(), m.getStatus().name(), m.getMatchStartTime(),
                m.getMatchEndTime(), m.getSkillLevels(), m.getOpponentName(), venue(venue),
                area(venue.getWardName(), venue.getProvinceName()), false, null, m.getCreatedAt(), m.getCreatedAt(),
                recruitments);
    }

    /** Trận ngoài sau khi sửa (dựng từ context + giá trị mới; {@code newVenue} null = giữ sân). */
    default ExternalMatchResponse toUpdatedExternalMatch(ExternalMatchView ctx, SelectableVenueView newVenue,
                                                         LocalDateTime startsAt, LocalDateTime endsAt,
                                                         List<String> skillLevels, String opponentName,
                                                         LocalDateTime updatedAt) {
        VenueRevealResponse venue = newVenue != null ? venue(newVenue) : venue(ctx.getVenueId(), ctx.getVenueName(),
                ctx.getVenueAddress(), ctx.getVenueLatitude(), ctx.getVenueLongitude());
        AreaResponse area = newVenue != null ? area(newVenue.getWardName(), newVenue.getProvinceName())
                : area(ctx.getWardName(), ctx.getProvinceName());
        return new ExternalMatchResponse(ctx.getExternalMatchId(), ctx.getTeamId(), ctx.getStatus(), startsAt, endsAt,
                skillLevels, opponentName, venue, area, Boolean.TRUE.equals(ctx.getHasApprovedMercenary()),
                ctx.getCancelledAt(), ctx.getCreatedAt(), updatedAt, null);
    }

    /** Đơn ứng tuyển; sân / tên đội trưởng khi người xem là đội trưởng hoặc đơn APPROVED (hiệu lực). */
    default ApplicationResponse toApplication(ApplicationView v, boolean viewerIsCaptain) {
        boolean reveal = viewerIsCaptain || APPROVED.equals(v.getEffectiveStatus());
        ApplicationResponse.Match match = new ApplicationResponse.Match(v.getSource(), v.getMatchId(),
                v.getExternalMatchId(), v.getOpponentName(), v.getGameStart(), v.getGameEnd(), v.getGameStatus(),
                area(v.getWardName(), v.getProvinceName()),
                reveal ? venue(v.getVenueId(), v.getVenueName(), v.getVenueAddress(), v.getVenueLatitude(),
                        v.getVenueLongitude()) : null);
        return new ApplicationResponse(v.getApplicationId(), v.getRecruitmentId(), v.getEffectiveStatus(),
                v.getConversationId(),
                applicant(v.getApplicantId(), v.getApplicantName(), v.getApplicantAvatarUrl(), v.getApplicantDeleted(),
                        v.getApplicantPreferredPosition()),
                new ApplicationResponse.Recruitment(v.getPosition(), v.getRecruitmentEffectiveStatus(),
                        new MercenaryTeamBriefResponse(v.getTeamId(), v.getTeamName(), v.getTeamAvatarUrl()), match,
                        reveal ? MapperSupport.maskName(v.getCaptainName(), v.getCaptainDeleted()) : null),
                v.getAppliedAt(), v.getRespondedAt(), v.getWithdrawnAt());
    }

    /** Đơn vừa nộp (PENDING → chưa hiện sân / đội trưởng). */
    default ApplicationResponse toApplied(ApplyContextView ctx, UUID callerId, MercenaryApplication application,
                                          UUID conversationId) {
        ApplicationResponse.Match match = new ApplicationResponse.Match(ctx.getSource(), ctx.getMatchId(),
                ctx.getExternalMatchId(), ctx.getOpponentName(), ctx.getGameStart(), ctx.getGameEnd(),
                ctx.getGameStatus(), area(ctx.getWardName(), ctx.getProvinceName()), null);
        return new ApplicationResponse(application.getId(), ctx.getRecruitmentId(), PENDING, conversationId,
                applicant(callerId, ctx.getCallerName(), ctx.getCallerAvatarUrl(), ctx.getCallerDeleted(),
                        ctx.getCallerPreferredPosition()),
                new ApplicationResponse.Recruitment(ctx.getPosition(), ctx.getEffectiveStatus(),
                        new MercenaryTeamBriefResponse(ctx.getTeamId(), ctx.getTeamName(), ctx.getTeamAvatarUrl()),
                        match, null),
                application.getAppliedAt(), null, null);
    }

    /** Ứng viên của tin + uy tín công khai (không email / số điện thoại). */
    default ApplicantResponse toApplicant(ApplicantRowView row) {
        return new ApplicantResponse(row.getApplicationId(), row.getEffectiveStatus(), row.getConversationId(),
                applicant(row.getUserId(), row.getDisplayName(), row.getAvatarUrl(), row.getDeleted(),
                        row.getPreferredPosition()),
                MercenaryReputationMapper.summary(row.getReliabilityScore(), row.getCompletedMatches(),
                        row.getCountedMatches(), row.getAverageRating(), row.getRatingCount(),
                        row.getVerifiedAt() != null),
                row.getAppliedAt(), row.getRespondedAt(), row.getWithdrawnAt());
    }

    /** Khoảng cách làm tròn lên bội số 0,5 km, tối thiểu 0,5 km — không suy ra được vị trí chính xác của sân. */
    static BigDecimal roundDistance(Double distanceKm) {
        if (distanceKm == null) {
            return null;
        }
        double step = MercenaryRules.DISTANCE_ROUNDING_KM;
        double rounded = Math.max(step, Math.ceil(distanceKm / step) * step);
        return BigDecimal.valueOf(rounded).setScale(1, RoundingMode.UNNECESSARY);
    }

    /** Trạng thái hiệu lực đơn của người xem: PENDING của tin đã đóng / hết hạn → REJECTED. */
    private static String effectiveApplicationStatus(String status, String recruitmentStatus) {
        return PENDING.equals(status) && TERMINAL_RECRUITMENT.contains(recruitmentStatus) ? REJECTED : status;
    }

    private static AreaResponse area(String wardName, String provinceName) {
        return wardName == null ? null : new AreaResponse(wardName, provinceName);
    }

    private static VenueRevealResponse venue(UUID id, String name, String address, BigDecimal latitude,
                                             BigDecimal longitude) {
        return id == null ? null : new VenueRevealResponse(id, name, address, latitude, longitude);
    }

    private static VenueRevealResponse venue(SelectableVenueView venue) {
        return new VenueRevealResponse(venue.getId(), venue.getName(), venue.getAddress(), venue.getLatitude(),
                venue.getLongitude());
    }

    private static ApplicantBriefResponse applicant(UUID userId, String name, String avatarUrl, Boolean deleted,
                                                    String preferredPosition) {
        return new ApplicantBriefResponse(userId, MapperSupport.maskName(name, deleted),
                MapperSupport.maskAvatar(avatarUrl, deleted), preferredPosition);
    }

    private static Integer toInt(Long value) {
        return value == null ? 0 : value.intValue();
    }
}
