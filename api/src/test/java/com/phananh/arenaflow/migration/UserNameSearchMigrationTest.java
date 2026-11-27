package com.phananh.arenaflow.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hợp đồng hồi quy cho Flyway V12 (hàm bỏ dấu tên + index GIN tìm theo tên). Chạy trên database dev cấu hình qua
 * .env.dev, chỉ sau khi V6–V11 đã áp.
 */
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=none")
class UserNameSearchMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsImmutableFoldAndSearchVectorFunctions() {
        assertThat(volatility("user_name_fold")).isEqualTo("i");
        assertThat(volatility("user_name_search_vector")).isEqualTo("i");
    }

    @Test
    void createsPartialGinIndexOnUserDisplayName() {
        String definition = jdbcTemplate.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE indexname = 'idx_user_name_search'", String.class);

        assertThat(definition)
                .contains("USING gin")
                .contains("user_name_search_vector(display_name)")
                .contains("deleted_at IS NULL");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "ĐẶNG  Thị-Ổi!!|dang thi oi",
            "Nguyễn Văn An|nguyen van an",
            "NGUYỄN VĂN AN|nguyen van an",
            "ng.an|ng an",
            "  Trần---An  |tran an",
            "Zoë|zo",
            "Team 2026|team 2026"
    })
    void foldsVietnameseNamesToSpaceSeparatedAsciiWords(String name, String expected) {
        assertThat(fold(name)).isEqualTo(expected);
    }

    @Test
    void foldsDecomposedUnicodeLikeComposed() {
        String decomposed = "Nguyễn Ánh";

        assertThat(fold(decomposed)).isEqualTo("nguyen anh");
    }

    @Test
    void searchVectorSupportsWordPrefixMatchOnly() {
        assertThat(matches("Lan Nguyễn", "an:*")).isFalse();
        assertThat(matches("Trần An", "an:*")).isTrue();
        assertThat(matches("Ánh Lê", "an:*")).isTrue();
        assertThat(matches("Nguyễn Văn An", "nguyen:* & an:*")).isTrue();
    }

    private String volatility(String function) {
        return jdbcTemplate.queryForObject(
                "SELECT provolatile::text FROM pg_proc WHERE proname = ?", String.class, function);
    }

    private String fold(String name) {
        return jdbcTemplate.queryForObject("SELECT user_name_fold(?)", String.class, name);
    }

    private boolean matches(String name, String tsQuery) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT user_name_search_vector(?) @@ to_tsquery('simple', ?)", Boolean.class, name, tsQuery));
    }
}
