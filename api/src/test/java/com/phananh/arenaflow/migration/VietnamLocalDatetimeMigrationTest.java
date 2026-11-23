package com.phananh.arenaflow.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hợp đồng hồi quy cho Flyway V6 (cần DB — chỉ chạy ở giai đoạn tích hợp trên Neon branch đã chốt).
 */
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=none")
class VietnamLocalDatetimeMigrationTest {

    private static final String VIETNAM_DEFAULT = "AT TIME ZONE 'Asia/Ho_Chi_Minh'";
    /** 22 bảng V6 đổi; loại flyway_schema_history (installed_on do Flyway quản lý). */
    private static final String V6_TABLES = V6TimestampColumns.COLUMNS.keySet().stream()
            .map(table -> "'" + table + "'")
            .collect(Collectors.joining(", ", "(", ")"));

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void noColumnKeepsTimeZoneAndEveryV6ColumnIsLocalTimestamp() {
        Integer withTimeZone = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = current_schema() AND data_type = 'timestamp with time zone'
                """, Integer.class);
        List<String> localTimestamps = jdbcTemplate.queryForList("""
                SELECT table_name || '.' || column_name FROM information_schema.columns
                WHERE table_schema = current_schema() AND data_type = 'timestamp without time zone'
                  AND table_name IN """ + V6_TABLES, String.class);

        assertThat(withTimeZone).isZero();
        // containsAll: migration sau thêm cột TIMESTAMP vào các bảng này không làm test đỏ.
        assertThat(localTimestamps).containsAll(V6TimestampColumns.pairs(V6TimestampColumns.COLUMNS));
    }

    @Test
    void everyV6DefaultUsesVietnamLocalTime() {
        Map<String, String> defaults = jdbcTemplate.query("""
                SELECT table_name || '.' || column_name AS col, column_default FROM information_schema.columns
                WHERE table_schema = current_schema() AND data_type = 'timestamp without time zone'
                  AND column_default IS NOT NULL AND table_name IN """ + V6_TABLES,
                (ResultSet rs) -> {
                    Map<String, String> result = new HashMap<>();
                    while (rs.next()) {
                        result.put(rs.getString("col"), rs.getString("column_default"));
                    }
                    return result;
                });

        // Thiếu cột → V6 làm mất DEFAULT; biểu thức sai → DEFAULT theo TimeZone phiên (lệch 7 giờ).
        assertThat(defaults).containsKeys(V6TimestampColumns.pairs(V6TimestampColumns.DEFAULTS).toArray(String[]::new));
        assertThat(defaults.values()).allSatisfy(value -> assertThat(value).contains(VIETNAM_DEFAULT));
    }

    @Test
    @Transactional
    void defaultIsVietnamLocalTimeEvenWhenSessionTimeZoneIsGmt() {
        jdbcTemplate.execute("SET LOCAL TimeZone = 'GMT'");
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO \"user\" (id, display_name) VALUES (?, 'V6 Default Probe')", userId);

        LocalDateTime createdAt = jdbcTemplate.queryForObject(
                "SELECT created_at FROM \"user\" WHERE id = ?", LocalDateTime.class, userId);
        LocalDateTime vietnamNow = jdbcTemplate.queryForObject(
                "SELECT now() AT TIME ZONE 'Asia/Ho_Chi_Minh'", LocalDateTime.class);

        assertThat(Duration.between(createdAt, vietnamNow).abs()).isLessThan(Duration.ofSeconds(5));
    }
}
