package com.phananh.arenaflow.application.dto.response;

public record PaginationMeta(int page, int pageSize, int totalPages, long totalItems) {

    public PaginationMeta(int page, int pageSize, long totalItems) {
        this(page, pageSize, (int) Math.ceil((double) totalItems / pageSize), totalItems);
    }
}
