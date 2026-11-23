package com.phananh.arenaflow.api.config;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

/**
 * Đọc LocalDateTime chặt: Jackson mặc định (lenient) bỏ "Z" rồi đọc như giờ Việt Nam (lệch 7 giờ âm thầm);
 * chặt thì chuỗi kèm Z / offset bị từ chối → 400. Chỉ đổi độ chặt khi đọc, không đổi định dạng ghi.
 */
@Configuration
public class JsonTimeConfig {

    /** Customizer cho JsonMapper của Spring Boot: field LocalDateTime từ chối chuỗi kèm "Z" hoặc offset. */
    @Bean
    public JsonMapperBuilderCustomizer strictLocalDateTimeCustomizer() {
        return builder -> builder.withConfigOverride(LocalDateTime.class,
                override -> override.setFormat(JsonFormat.Value.forLeniency(false)));
    }
}
