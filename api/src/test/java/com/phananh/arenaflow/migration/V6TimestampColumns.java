package com.phananh.arenaflow.migration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Danh sách cột V6 chuyển TIMESTAMPTZ → TIMESTAMP, dùng chung cho test tĩnh và test DB của V6. */
final class V6TimestampColumns {

    private static final Map<String, List<String>> columns = new LinkedHashMap<>();
    private static final Map<String, List<String>> defaults = new LinkedHashMap<>();

    static {
        table("administrative_unit", List.of("created_at"), List.of("created_at"));
        table("user", List.of("created_at", "updated_at", "deleted_at"), List.of("created_at", "updated_at"));
        table("oauth_account", List.of("created_at"), List.of("created_at"));
        table("team", List.of("created_at", "updated_at", "disbanded_at"), List.of("created_at", "updated_at"));
        table("team_member", List.of("invited_at", "joined_at", "left_at"), List.of("invited_at"));
        table("team_captain_history", List.of("transferred_at"), List.of("transferred_at"));
        table("sport", List.of("created_at"), List.of("created_at"));
        table("venue", List.of("last_selected_at", "admin_verified_inactive_at", "created_at", "updated_at",
                "stale_alerted_at", "closure_alerted_at"), List.of("created_at", "updated_at"));
        table("venue_image", List.of("created_at"), List.of("created_at"));
        table("venue_report", List.of("created_at", "handled_at"), List.of("created_at"));
        table("match", List.of("match_start_time", "match_end_time", "expires_at", "confirmed_match_time",
                "cancelled_at", "created_at", "updated_at"), List.of("created_at", "updated_at"));
        table("match_venue_option", List.of("created_at"), List.of("created_at"));
        table("match_time_option", List.of("proposed_start_time", "proposed_end_time", "created_at"),
                List.of("created_at"));
        table("cost_custom_item", List.of("created_at"), List.of("created_at"));
        table("match_request", List.of("requested_at", "responded_at", "withdrawn_at"), List.of("requested_at"));
        table("pairing", List.of("author_confirmed_at", "interested_confirmed_at", "final_confirmed_at",
                "created_at"), List.of("created_at"));
        table("rating", List.of("created_at"), List.of("created_at"));
        table("report", List.of("admin_reviewed_at", "created_at"), List.of("created_at"));
        table("mercenary_recruitment", List.of("created_at", "updated_at"), List.of("created_at", "updated_at"));
        table("mercenary_application", List.of("applied_at", "responded_at", "withdrawn_at"),
                List.of("applied_at"));
        table("chat_message", List.of("created_at"), List.of("created_at"));
        table("notification", List.of("created_at"), List.of("created_at"));
    }

    /** Bảng → cột TIMESTAMPTZ của V1/V4/V5 (team_tag đã bị V2 xoá). */
    static final Map<String, List<String>> COLUMNS = Collections.unmodifiableMap(columns);
    /** Bảng → cột có DEFAULT NOW(). */
    static final Map<String, List<String>> DEFAULTS = Collections.unmodifiableMap(defaults);

    private V6TimestampColumns() {
    }

    private static void table(String name, List<String> tableColumns, List<String> tableDefaults) {
        columns.put(name, tableColumns);
        defaults.put(name, tableDefaults);
    }

    /** Cặp "bảng.cột" để so với information_schema. */
    static Set<String> pairs(Map<String, List<String>> byTable) {
        return byTable.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream().map(column -> entry.getKey() + "." + column))
                .collect(Collectors.toSet());
    }
}
