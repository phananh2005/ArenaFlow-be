package com.phananh.ArenaFlow.mapper;

import com.phananh.ArenaFlow.dto.response.AdminTeamSummaryResponse;
import com.phananh.ArenaFlow.dto.response.CaptainHistoryResponse;
import com.phananh.ArenaFlow.dto.response.InvitationResponse;
import com.phananh.ArenaFlow.dto.response.MemberResponse;
import com.phananh.ArenaFlow.dto.response.ProfileResponse;
import com.phananh.ArenaFlow.dto.response.TeamResponse;
import com.phananh.ArenaFlow.dto.response.TeamSummaryResponse;
import com.phananh.ArenaFlow.entity.team.TeamMember;
import com.phananh.ArenaFlow.entity.user.OAuthAccount;
import com.phananh.ArenaFlow.repository.projection.CaptainHistoryView;
import com.phananh.ArenaFlow.repository.projection.MemberView;
import com.phananh.ArenaFlow.repository.projection.ProfileView;
import com.phananh.ArenaFlow.repository.projection.TeamDetailView;
import com.phananh.ArenaFlow.repository.projection.TeamSummaryView;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MapperTest {

    private static final Instant CREATED = Instant.parse("2026-11-01T10:00:00Z");
    private static final OffsetDateTime CREATED_UTC = OffsetDateTime.of(2026, 11, 1, 10, 0, 0, 0, ZoneOffset.UTC);

    private final SpelAwareProxyProjectionFactory projections = new SpelAwareProxyProjectionFactory();
    private final ProfileMapper profileMapper = Mappers.getMapper(ProfileMapper.class);
    private final TeamMapper teamMapper = Mappers.getMapper(TeamMapper.class);
    private final MembershipMapper membershipMapper = Mappers.getMapper(MembershipMapper.class);

    private <T> T projection(Class<T> type, Map<String, Object> values) {
        return projections.createProjection(type, values);
    }

    @Test
    void profileMapperBuildsWardUppercasesProviderAndConvertsTimeToUtc() {
        UUID userId = UUID.randomUUID();
        UUID wardId = UUID.randomUUID();
        UUID provinceId = UUID.randomUUID();
        Map<String, Object> values = new HashMap<>();
        values.put("userId", userId);
        values.put("role", "USER");
        values.put("displayName", "User 1a2b3c");
        values.put("dateOfBirth", LocalDate.of(1995, 5, 15));
        values.put("wardId", wardId);
        values.put("wardName", "Phường Bến Nghé");
        values.put("provinceId", provinceId);
        values.put("provinceName", "TP. Hồ Chí Minh");
        values.put("createdAt", CREATED);
        values.put("updatedAt", CREATED);
        OAuthAccount account = new OAuthAccount();
        account.setProvider(OAuthAccount.Provider.google);
        account.setProviderUserId("user@example.com");

        ProfileResponse response = profileMapper.toResponse(projection(ProfileView.class, values), List.of(account));

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.displayName()).isEqualTo("User 1a2b3c");
        assertThat(response.dateOfBirth()).isEqualTo(LocalDate.of(1995, 5, 15));
        assertThat(response.providers()).singleElement().satisfies(provider -> {
            assertThat(provider.provider()).isEqualTo("GOOGLE");
            assertThat(provider.providerUserId()).isEqualTo("user@example.com");
        });
        assertThat(response.ward()).isNotNull();
        assertThat(response.ward().wardId()).isEqualTo(wardId);
        assertThat(response.ward().provinceName()).isEqualTo("TP. Hồ Chí Minh");
        assertThat(response.createdAt()).isEqualTo(CREATED_UTC);
    }

    @Test
    void profileMapperReturnsNullWardWhenNotChosen() {
        Map<String, Object> values = new HashMap<>();
        values.put("userId", UUID.randomUUID());
        values.put("role", "ADMIN");

        ProfileResponse response = profileMapper.toResponse(projection(ProfileView.class, values), List.of());

        assertThat(response.ward()).isNull();
        assertThat(response.providers()).isEmpty();
    }

    @Test
    void teamMapperParsesSkillLevelsAndMasksDeletedCaptain() {
        Map<String, Object> values = new HashMap<>();
        values.put("teamId", UUID.randomUUID());
        values.put("teamName", "Team Alpha");
        values.put("skillLevels", "[\"Nhiệt tình\",\"Rèn luyện\"]");
        values.put("status", "ACTIVE");
        values.put("captainId", UUID.randomUUID());
        values.put("captainName", "Nguyễn Văn A");
        values.put("captainDeleted", true);
        values.put("memberCount", 5L);
        values.put("createdAt", CREATED);
        values.put("updatedAt", CREATED);

        TeamResponse response = teamMapper.toResponse(projection(TeamDetailView.class, values));

        assertThat(response.skillLevels()).containsExactly("Nhiệt tình", "Rèn luyện");
        assertThat(response.captainName()).isEqualTo("Người dùng đã xóa");
        assertThat(response.captainId()).isNotNull();
        assertThat(response.memberCount()).isEqualTo(5L);
        assertThat(response.createdAt()).isEqualTo(CREATED_UTC);
    }

    @Test
    void teamMapperSummariesKeepCaptainNameWhenNotDeletedAndAdminHasReason() {
        Map<String, Object> values = new HashMap<>();
        values.put("teamId", UUID.randomUUID());
        values.put("teamName", "Team Beta");
        values.put("skillLevels", "[]");
        values.put("status", "INACTIVE");
        values.put("inactiveReason", "Vi phạm");
        values.put("captainName", "Trần B");
        values.put("captainDeleted", false);
        values.put("memberCount", 1L);
        values.put("createdAt", CREATED);
        TeamSummaryView view = projection(TeamSummaryView.class, values);

        TeamSummaryResponse summary = teamMapper.toSummary(view);
        AdminTeamSummaryResponse admin = teamMapper.toAdminSummary(view);

        assertThat(summary.captainName()).isEqualTo("Trần B");
        assertThat(summary.skillLevels()).isEmpty();
        assertThat(admin.inactiveReason()).isEqualTo("Vi phạm");
        assertThat(admin.createdAt()).isEqualTo(CREATED_UTC);
    }

    @Test
    void teamMapperCaptainHistoryMasksOnlyDeletedSide() {
        Map<String, Object> values = new HashMap<>();
        values.put("historyId", UUID.randomUUID());
        values.put("teamId", UUID.randomUUID());
        values.put("fromUserId", UUID.randomUUID());
        values.put("fromUserName", "First");
        values.put("fromUserDeleted", false);
        values.put("toUserId", UUID.randomUUID());
        values.put("toUserName", "Second");
        values.put("toUserDeleted", true);
        values.put("reason", "Bận");
        values.put("transferredAt", CREATED);

        CaptainHistoryResponse response = teamMapper.toResponse(projection(CaptainHistoryView.class, values));

        assertThat(response.fromUserName()).isEqualTo("First");
        assertThat(response.toUserName()).isEqualTo("Người dùng đã xóa");
        assertThat(response.reason()).isEqualTo("Bận");
        assertThat(response.transferredAt()).isEqualTo(CREATED_UTC);
    }

    @Test
    void membershipMapperBuildsInvitationAndMasksDeletedMember() {
        TeamMember member = new TeamMember();
        member.setTeamId(UUID.randomUUID());
        member.setUserId(UUID.randomUUID());
        member.setRole(TeamMember.Role.MEMBER);
        member.setStatus(TeamMember.Status.PENDING);
        member.setInvitedAt(CREATED_UTC);

        InvitationResponse invitation = membershipMapper.toInvitation(member, "Team Alpha");

        assertThat(invitation.teamName()).isEqualTo("Team Alpha");
        assertThat(invitation.userId()).isEqualTo(member.getUserId());
        assertThat(invitation.status()).isEqualTo("PENDING");
        assertThat(invitation.invitedAt()).isEqualTo(CREATED_UTC);

        Map<String, Object> values = new HashMap<>();
        values.put("memberId", UUID.randomUUID());
        values.put("teamId", UUID.randomUUID());
        values.put("userId", UUID.randomUUID());
        values.put("displayName", "Gone");
        values.put("avatarUrl", "https://cdn.example.com/x.png");
        values.put("userDeleted", true);
        values.put("role", "MEMBER");
        values.put("status", "ACTIVE");
        values.put("joinedAt", CREATED);

        MemberResponse response = membershipMapper.toMember(projection(MemberView.class, values));

        assertThat(response.displayName()).isEqualTo("Người dùng đã xóa");
        assertThat(response.avatarUrl()).isNull();
        assertThat(response.role()).isEqualTo("MEMBER");
        assertThat(response.joinedAt()).isEqualTo(CREATED_UTC);
    }
}
