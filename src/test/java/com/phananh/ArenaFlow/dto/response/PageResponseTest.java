package com.phananh.ArenaFlow.dto.response;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageResponseTest {

    @Test
    void containsItemsAndPaginationMetadata() {
        PaginationMeta pagination = new PaginationMeta(2, 10, 25);
        PageResponse<String> response = new PageResponse<>(List.of("one"), pagination);

        assertThat(response.data()).containsExactly("one");
        assertThat(response.pagination().page()).isEqualTo(2);
        assertThat(response.pagination().pageSize()).isEqualTo(10);
        assertThat(response.pagination().totalPages()).isEqualTo(3);
        assertThat(response.pagination().totalItems()).isEqualTo(25);
    }
}
