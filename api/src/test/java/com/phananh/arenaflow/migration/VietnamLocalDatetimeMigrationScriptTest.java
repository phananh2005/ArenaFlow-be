package com.phananh.arenaflow.migration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Kiểm tĩnh nội dung V6 (không cần DB): đủ bảng/cột, mỗi bảng một ALTER TABLE, không now() trần. */
class VietnamLocalDatetimeMigrationScriptTest {

    private static final String SCRIPT = "db/migration/V6__vietnam_local_datetime.sql";
    private static final String VN = "'Asia/Ho_Chi_Minh'";

    private static String sql;

    @BeforeAll
    static void load() throws IOException {
        try (InputStream in = VietnamLocalDatetimeMigrationScriptTest.class.getClassLoader().getResourceAsStream(SCRIPT)) {
            sql = new String(Objects.requireNonNull(in, SCRIPT).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void coversExactly51ColumnsIn22TablesWith27Defaults() {
        assertThat(V6TimestampColumns.COLUMNS).hasSize(22);
        assertThat(V6TimestampColumns.COLUMNS.values().stream().mapToInt(List::size).sum()).isEqualTo(51);
        assertThat(V6TimestampColumns.DEFAULTS.values().stream().mapToInt(List::size).sum()).isEqualTo(27);

        // Đầu dòng: bỏ qua chữ "ALTER TABLE" trong comment.
        assertThat(Pattern.compile("^ALTER TABLE ", Pattern.MULTILINE).matcher(sql).results().count()).isEqualTo(22);
        assertThat(count(" TYPE TIMESTAMP USING ")).isEqualTo(51);
        assertThat(count(" DROP DEFAULT")).isEqualTo(27);
        assertThat(count(" SET DEFAULT (now() AT TIME ZONE " + VN + ")")).isEqualTo(27);
    }

    @Test
    void eachTableIsOneStatementConvertingEveryColumnAndDefault() {
        V6TimestampColumns.COLUMNS.forEach((table, columns) -> {
            String statement = statement(table);
            for (String column : columns) {
                assertThat(statement).as(table + "." + column).contains("ALTER COLUMN " + column
                        + " TYPE TIMESTAMP USING " + column + " AT TIME ZONE " + VN);
            }
            for (String column : V6TimestampColumns.DEFAULTS.get(table)) {
                int drop = statement.indexOf("ALTER COLUMN " + column + " DROP DEFAULT");
                int type = statement.indexOf("ALTER COLUMN " + column + " TYPE TIMESTAMP");
                int set = statement.indexOf("ALTER COLUMN " + column + " SET DEFAULT (now() AT TIME ZONE " + VN + ")");
                assertThat(drop).as(table + "." + column + " DROP DEFAULT").isNotNegative().isLessThan(type);
                assertThat(set).as(table + "." + column + " SET DEFAULT").isGreaterThan(type);
            }
        });
    }

    @Test
    void neverUsesBareNowOrSessionDependentClock() {
        String withoutVietnamNow = sql.replace("now() AT TIME ZONE " + VN, "");
        assertThat(withoutVietnamNow.toLowerCase())
                .doesNotContain("now()")
                .doesNotContain("current_timestamp")
                .doesNotContain("localtimestamp")
                .doesNotContain("current_date")
                .doesNotContain("timestamptz")
                .doesNotContain("with time zone");
    }

    private static int count(String token) {
        Matcher matcher = Pattern.compile(Pattern.quote(token)).matcher(sql);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static String statement(String table) {
        // "user" là từ khoá PostgreSQL → script viết trong ngoặc kép.
        String name = "user".equals(table) ? "\"user\"" : table;
        int start = sql.indexOf("ALTER TABLE " + name + "\n");
        if (start < 0) {
            start = sql.indexOf("ALTER TABLE " + name + "\r\n");
        }
        assertThat(start).as("ALTER TABLE " + name).isNotNegative();
        return sql.substring(start, sql.indexOf(';', start));
    }
}
