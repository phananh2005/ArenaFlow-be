package com.phananh.arenaflow.application.dto.response;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Hình dạng JSON của response tra cứu người dùng: thứ tự field, null giữ nguyên, thời gian không kèm múi giờ. */
class UserDiscoveryResponseJsonTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.propertyNames().forEach(names::add);
        return names;
    }

    @Test
    void catalogItemsHaveIdAndNameOnly() {
        assertThat(fieldNames(jsonMapper.valueToTree(new ProvinceResponse(UUID.randomUUID(), "Hà Nội"))))
                .containsExactly("provinceId", "provinceName");
        assertThat(fieldNames(jsonMapper.valueToTree(new WardSummaryResponse(UUID.randomUUID(), "Phường A"))))
                .containsExactly("wardId", "wardName");
    }

    @Test
    void candidateListHasItemsAndHasMoreWithoutPhoneNumber() {
        InvitationCandidatesResponse response = new InvitationCandidatesResponse(List.of(
                new InvitationCandidateResponse(UUID.randomUUID(), "An Phạm", null, null)), true);

        JsonNode json = jsonMapper.valueToTree(response);

        assertThat(fieldNames(json)).containsExactly("items", "hasMore");
        assertThat(json.get("hasMore").asBoolean()).isTrue();
        JsonNode item = json.get("items").get(0);
        assertThat(fieldNames(item)).containsExactly("userId", "displayName", "avatarUrl", "membershipStatus");
        assertThat(item.get("membershipStatus").isNull()).isTrue();
        assertThat(json.toString()).doesNotContain("phone");
    }

    @Test
    void myInvitationNestsTeamAndWritesLocalDateTimeWithoutOffset() {
        MyInvitationResponse response = new MyInvitationResponse(UUID.randomUUID(),
                LocalDateTime.of(2026, 11, 17, 19, 30),
                new MyInvitationTeamResponse(UUID.randomUUID(), "Team Alpha", null, List.of("Nhiệt tình"),
                        "INACTIVE", "Hùng", 5));

        JsonNode json = jsonMapper.valueToTree(response);

        assertThat(fieldNames(json)).containsExactly("invitationId", "invitedAt", "team");
        assertThat(json.get("invitedAt").asString()).isEqualTo("2026-11-17T19:30:00");
        assertThat(fieldNames(json.get("team"))).containsExactly("teamId", "teamName", "avatarUrl", "skillLevels",
                "status", "captainName", "memberCount");
        assertThat(json.get("team").get("memberCount").asLong()).isEqualTo(5);
    }

    @Test
    void myTeamFieldsFollowApiOrder() {
        MyTeamResponse response = new MyTeamResponse(UUID.randomUUID(), "Team Beta", null, List.of(), "ACTIVE", null,
                8, "MEMBER", LocalDateTime.of(2026, 11, 10, 19, 0, 0, 500_000_000));

        JsonNode json = jsonMapper.valueToTree(response);

        assertThat(fieldNames(json)).containsExactly("teamId", "teamName", "avatarUrl", "skillLevels", "status",
                "captainName", "memberCount", "role", "joinedAt");
        assertThat(json.get("captainName").isNull()).isTrue();
        assertThat(json.get("joinedAt").asString()).isEqualTo("2026-11-10T19:00:00.5");
    }
}
