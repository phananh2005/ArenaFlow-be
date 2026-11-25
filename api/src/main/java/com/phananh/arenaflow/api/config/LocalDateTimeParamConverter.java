package com.phananh.arenaflow.api.config;

import org.springframework.core.convert.converter.Converter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Query param / path thời gian: chỉ nhận ISO-8601 giờ địa phương (giờ Việt Nam) không kèm múi giờ; chuỗi có
 * {@code Z}/offset bị từ chối (parser ISO mặc định của Spring nhận offset rồi bỏ đi, làm lệch giờ âm thầm). Lỗi parse
 * thành 400 VALIDATION_ERROR gắn tên tham số.
 */
public class LocalDateTimeParamConverter implements Converter<String, LocalDateTime> {

    @Override
    public LocalDateTime convert(String source) {
        String value = source.trim();
        return value.isEmpty() ? null : LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
