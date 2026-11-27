package com.phananh.arenaflow.migration;

import com.phananh.arenaflow.domain.entity.chat.Notification;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Enum của entity phải khớp đúng danh sách giá trị trong CHECK của V11 — không cần DB. */
class NotificationTypeCheckConsistencyTest {

    private static final Pattern QUOTED = Pattern.compile("'([A-Z_]+)'");

    @Test
    void typeEnumMatchesTypeCheckOfV11() throws IOException {
        String sql = readV11();
        Set<String> checkValues = quotedValues(between(sql, "notification_type_check CHECK", ");"));

        assertThat(checkValues).hasSize(35);
        assertThat(checkValues).containsExactlyInAnyOrderElementsOf(names(Notification.Type.values()));
    }

    @Test
    void referenceTypeEnumMatchesReferenceTypeCheckOfV11() throws IOException {
        String sql = readV11();
        Set<String> checkValues = quotedValues(between(sql, "notification_reference_type_check CHECK", ");"));

        assertThat(checkValues).hasSize(10);
        assertThat(checkValues).containsExactlyInAnyOrderElementsOf(names(Notification.ReferenceType.values()));
    }

    @Test
    void v11KeepsEveryTypeOfV1() throws IOException {
        Set<String> v1Types = Set.of("MATCH_REQUEST_RECEIVED", "REQUEST_ACCEPTED", "REQUEST_REJECTED", "MATCH_CONFIRMED",
                "MATCH_CANCELLED", "PAIRING_NEW", "REMINDER_BEFORE_MATCH", "MATCH_COMPLETED", "RATING_RECEIVED",
                "REPORT_RECEIVED", "MERCENARY_APPLICATION", "TEAM_INVITATION");

        assertThat(quotedValues(between(readV11(), "notification_type_check CHECK", ");"))).containsAll(v1Types);
    }

    private static String readV11() throws IOException {
        try (InputStream in = NotificationTypeCheckConsistencyTest.class.getClassLoader()
                .getResourceAsStream("db/migration/V11__notification.sql")) {
            assertThat(in).as("V11__notification.sql trên classpath").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String between(String text, String start, String end) {
        int from = text.indexOf(start);
        assertThat(from).as("tìm thấy " + start).isGreaterThanOrEqualTo(0);
        return text.substring(from, text.indexOf(end, from));
    }

    private static Set<String> quotedValues(String fragment) {
        Set<String> values = new LinkedHashSet<>();
        Matcher matcher = QUOTED.matcher(fragment);
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
        return values;
    }

    private static Set<String> names(Enum<?>[] values) {
        return new LinkedHashSet<>(Arrays.stream(values).map(Enum::name).toList());
    }
}
