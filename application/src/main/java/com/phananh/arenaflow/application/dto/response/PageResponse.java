package com.phananh.arenaflow.application.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

public record PageResponse<T>(List<T> data, PaginationMeta pagination) {

    /** Dựng response phân trang từ Page của repository; {@code page} đánh số từ 1 như API. */
    public static <S, T> PageResponse<T> from(Page<S> source, Function<S, T> mapper, int page, int pageSize) {
        return new PageResponse<>(source.getContent().stream().map(mapper).toList(),
                new PaginationMeta(page, pageSize, source.getTotalElements()));
    }
}
