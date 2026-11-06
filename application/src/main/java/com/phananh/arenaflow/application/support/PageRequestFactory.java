package com.phananh.arenaflow.application.support;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
public class PageRequestFactory {

    /**
     * Nhận page đánh số từ 1, chuyển sang chỉ số Spring Data từ 0; pageSize từ 1 đến 100; ngoài khoảng
     * → IllegalArgumentException.
     */
    public Pageable create(int page, int pageSize) {
        if (page < 1) {
            throw new IllegalArgumentException("page must be at least 1");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("pageSize must be between 1 and 100");
        }
        return PageRequest.of(page - 1, pageSize);
    }
}
