package com.phananh.arenaflow.api.config;

import tools.jackson.databind.json.JsonMapper;

/** JsonMapper cho MockMvc standalone, áp các customizer đọc chặt như ứng dụng. */
final class JsonTestSupport {

    private JsonTestSupport() {
    }

    static JsonMapper applicationJsonMapper() {
        JsonMapper.Builder builder = JsonMapper.builder();
        new JsonTimeConfig().strictLocalDateTimeCustomizer().customize(builder);
        new JsonNumberConfig().strictIntegerCustomizer().customize(builder);
        return builder.build();
    }
}
