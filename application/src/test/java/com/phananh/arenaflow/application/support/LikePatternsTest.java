package com.phananh.arenaflow.application.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Kiểm mẫu LIKE "chứa từ khoá": ký tự đại diện trong từ khoá được so khớp nguyên văn, không có từ khoá thì không lọc. */
class LikePatternsTest {

    @Test
    void wrapsKeywordAndEscapesWildcardsAndBackslash() {
        assertThat(LikePatterns.contains("hoa")).isEqualTo("%hoa%");
        assertThat(LikePatterns.contains("100%_a\\b")).isEqualTo("%100\\%\\_a\\\\b%");
    }

    @Test
    void missingKeywordMeansNoFilter() {
        assertThat(LikePatterns.contains(null)).isNull();
        assertThat(LikePatterns.contains("")).isNull();
    }
}
