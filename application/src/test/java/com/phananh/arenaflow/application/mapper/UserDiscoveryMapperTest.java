package com.phananh.arenaflow.application.mapper;

import com.phananh.arenaflow.application.dto.response.InvitationCandidateResponse;
import com.phananh.arenaflow.application.dto.response.MyInvitationResponse;
import com.phananh.arenaflow.application.dto.response.MyTeamResponse;
import com.phananh.arenaflow.application.dto.response.ProvinceResponse;
import com.phananh.arenaflow.application.dto.response.WardSummaryResponse;
import com.phananh.arenaflow.domain.projection.InvitationCandidateView;
import com.phananh.arenaflow.domain.projection.MyInvitationView;
import com.phananh.arenaflow.domain.projection.MyTeamView;
import com.phananh.arenaflow.domain.projection.ProvinceView;
import com.phananh.arenaflow.domain.projection.ProvinceWardView;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Mapper của danh mục khu vực, tìm người để mời, lời mời của tôi, đội của tôi. */
class UserDiscoveryMapperTest {

    private static final LocalDateTime TIME = LocalDateTime.of(2026, 11, 17, 19, 30, 0, 123_456_000);

    private final SpelAwareProxyProjectionFactory projections = new SpelAwareProxyProjectionFactory();
    private final AdministrativeUnitMapper administrativeUnitMapper = Mappers.getMapper(AdministrativeUnitMapper.class);
    private final MembershipMapper membershipMapper = Mappers.getMapper(MembershipMapper.class);
    private final TeamMapper teamMapper = Mappers.getMapper(TeamMapper.class);

    private <T> T projection(Class<T> type, Map<String, Object> values) {
        return projections.createProjection(type, values);
    }

    private Map<String, Object> teamValues(String captainName, Boolean captainDeleted) {
        Map<String, Object> values = new HashMap<>();
        values.put("teamId", UUID.randomUUID());
        values.put("teamName", "Team Alpha");
        values.put("avatarUrl", null);
        values.put("skillLevels", "[\"Nhiệt tình\",\"Rèn luyện\"]");
        values.put("status", "INACTIVE");
        values.put("captainName", captainName);
        values.put("captainDeleted", captainDeleted);
        values.put("memberCount", 5L);
        return values;
    }

    @Test
    void mapsProvinceAndWardSummary() {
        UUID provinceId = UUID.randomUUID();
        UUID wardId = UUID.randomUUID();

        ProvinceResponse province = administrativeUnitMapper.toProvince(projection(ProvinceView.class,
                Map.of("provinceId", provinceId, "provinceName", "Đà Nẵng")));
        WardSummaryResponse ward = administrativeUnitMapper.toWardSummary(projection(ProvinceWardView.class,
                Map.of("provinceId", provinceId, "wardId", wardId, "wardName", "Phường Bến Nghé")));

        assertThat(province).isEqualTo(new ProvinceResponse(provinceId, "Đà Nẵng"));
        assertThat(ward).isEqualTo(new WardSummaryResponse(wardId, "Phường Bến Nghé"));
    }

    @Test
    void candidateKeepsNameAndMembershipStatus() {
        UUID userId = UUID.randomUUID();
        Map<String, Object> values = new HashMap<>();
        values.put("userId", userId);
        values.put("displayName", "Nguyễn Văn An");
        values.put("avatarUrl", "https://cdn.example.com/an.png");
        values.put("membershipStatus", "PENDING");

        InvitationCandidateResponse response = membershipMapper.toCandidate(
                projection(InvitationCandidateView.class, values));

        assertThat(response).isEqualTo(new InvitationCandidateResponse(userId, "Nguyễn Văn An",
                "https://cdn.example.com/an.png", "PENDING"));
    }

    @Test
    void myInvitationNestsTeamKeepsLocalTimeAndParsesSkillLevels() {
        UUID invitationId = UUID.randomUUID();
        Map<String, Object> values = teamValues("Hùng", false);
        values.put("invitationId", invitationId);
        values.put("invitedAt", TIME);

        MyInvitationResponse response = membershipMapper.toMyInvitation(projection(MyInvitationView.class, values));

        assertThat(response.invitationId()).isEqualTo(invitationId);
        assertThat(response.invitedAt()).isEqualTo(TIME);
        assertThat(response.team().teamId()).isEqualTo(values.get("teamId"));
        assertThat(response.team().teamName()).isEqualTo("Team Alpha");
        assertThat(response.team().avatarUrl()).isNull();
        assertThat(response.team().skillLevels()).containsExactly("Nhiệt tình", "Rèn luyện");
        assertThat(response.team().status()).isEqualTo("INACTIVE");
        assertThat(response.team().captainName()).isEqualTo("Hùng");
        assertThat(response.team().memberCount()).isEqualTo(5);
    }

    @Test
    void myInvitationMasksDeletedCaptainAndKeepsNullCaptain() {
        Map<String, Object> deleted = teamValues("Real Name", true);
        deleted.put("invitationId", UUID.randomUUID());
        deleted.put("invitedAt", TIME);
        Map<String, Object> noCaptain = teamValues(null, null);
        noCaptain.put("invitationId", UUID.randomUUID());
        noCaptain.put("invitedAt", TIME);
        noCaptain.put("skillLevels", "[]");

        assertThat(membershipMapper.toMyInvitation(projection(MyInvitationView.class, deleted)).team().captainName())
                .isEqualTo("Người dùng đã xóa");
        MyInvitationResponse withoutCaptain = membershipMapper.toMyInvitation(
                projection(MyInvitationView.class, noCaptain));
        assertThat(withoutCaptain.team().captainName()).isNull();
        assertThat(withoutCaptain.team().skillLevels()).isEqualTo(List.of());
    }

    @Test
    void myTeamMasksDeletedCaptainAndKeepsRoleAndLocalJoinedAt() {
        Map<String, Object> values = teamValues("Real Captain", true);
        values.put("role", "MEMBER");
        values.put("joinedAt", TIME);

        MyTeamResponse response = teamMapper.toMyTeam(projection(MyTeamView.class, values));

        assertThat(response.teamId()).isEqualTo(values.get("teamId"));
        assertThat(response.captainName()).isEqualTo("Người dùng đã xóa");
        assertThat(response.skillLevels()).containsExactly("Nhiệt tình", "Rèn luyện");
        assertThat(response.status()).isEqualTo("INACTIVE");
        assertThat(response.memberCount()).isEqualTo(5);
        assertThat(response.role()).isEqualTo("MEMBER");
        assertThat(response.joinedAt()).isEqualTo(TIME);
    }
}
