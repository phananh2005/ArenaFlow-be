package com.phananh.arenaflow.api.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;

/**
 * Đọc field số nguyên chặt: Jackson mặc định cắt phần thập phân (4.5 → 4) và nhận chuỗi số ("4") âm thầm;
 * chặt thì số có dấu chấm (kể cả 4.0) hoặc chuỗi số bị từ chối → 400. Chuỗi rỗng "" giữ mặc định của Jackson:
 * field kiểu object (Integer, Long) đọc như null, field int nguyên thuỷ bị từ chối.
 */
@Configuration
public class JsonNumberConfig {

    /** Customizer cho JsonMapper của Spring Boot: field số nguyên từ chối số có phần thập phân và chuỗi số. */
    @Bean
    public JsonMapperBuilderCustomizer strictIntegerCustomizer() {
        return builder -> builder.withCoercionConfig(LogicalType.Integer, config -> config
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.String, CoercionAction.Fail));
    }
}
