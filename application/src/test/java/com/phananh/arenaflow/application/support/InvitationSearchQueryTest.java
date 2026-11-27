package com.phananh.arenaflow.application.support;

import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvitationSearchQueryTest {

    @ParameterizedTest
    @ValueSource(strings = {"0987654321", "+84 987 654 321", "84987654321", "0987.654.321", "0987-654-321",
            "  0987654321  ", "+84987654321"})
    void phoneLikeInputIsNormalizedToLocalTenDigits(String q) {
        assertThat(InvitationSearchQuery.parse(q))
                .isEqualTo(new InvitationSearchQuery.Phone("0987654321"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"+84 12345", "09+87654321", "--", "123", "098765432", "09876543210", "+0987654321",
            "1987654321", ". -"})
    void invalidPhoneLikeInputIsRejectedWithUser005(String q) {
        assertThatThrownBy(() -> InvitationSearchQuery.parse(q))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.USER_005);
    }

    @Test
    void nonPhoneInputGoesToNameBranchTrimmedButNotFolded() {
        assertThat(InvitationSearchQuery.parse("  Nguyễn An ")).isEqualTo(new InvitationSearchQuery.Name("Nguyễn An"));
        assertThat(InvitationSearchQuery.parse("!!")).isEqualTo(new InvitationSearchQuery.Name("!!"));
        assertThat(InvitationSearchQuery.parse("ng.an")).isEqualTo(new InvitationSearchQuery.Name("ng.an"));
        assertThat(InvitationSearchQuery.parse("0987 abc")).isEqualTo(new InvitationSearchQuery.Name("0987 abc"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "a", " ", "a ", "!", "é"})
    void foldedNameShorterThanTwoAlphanumericCharsIsRejectedWithUser006(String folded) {
        assertThatThrownBy(() -> InvitationSearchQuery.ofFolded(folded))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.USER_006);
    }

    @Test
    void nullFoldedNameIsRejectedWithUser006() {
        assertThatThrownBy(() -> InvitationSearchQuery.ofFolded(null))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.USER_006);
    }

    @Test
    void foldedNameBuildsPrefixTsQueryAndPrefix() {
        assertThat(InvitationSearchQuery.ofFolded("nguyen an"))
                .isEqualTo(new InvitationSearchQuery.FoldedName("nguyen:* & an:*", "nguyen an"));
        assertThat(InvitationSearchQuery.ofFolded("a b"))
                .isEqualTo(new InvitationSearchQuery.FoldedName("a:* & b:*", "a b"));
        assertThat(InvitationSearchQuery.ofFolded("an"))
                .isEqualTo(new InvitationSearchQuery.FoldedName("an:*", "an"));
    }

    @Test
    void tokensWithCharactersOutsideLowercaseAlphanumericAreDropped() {
        // Lớp phòng thủ: ký tự cú pháp tsquery không bao giờ lọt vào chuỗi bind.
        assertThat(InvitationSearchQuery.ofFolded("nguyen a&b x:* an  ")).isEqualTo(
                new InvitationSearchQuery.FoldedName("nguyen:* & an:*", "nguyen an"));
        assertThatThrownBy(() -> InvitationSearchQuery.ofFolded("AN a'b !"))
                .isInstanceOf(BusinessException.class);
    }
}
