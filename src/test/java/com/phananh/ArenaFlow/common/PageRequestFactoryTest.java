package com.phananh.ArenaFlow.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageRequestFactoryTest {

    private final PageRequestFactory factory = new PageRequestFactory();

    @Test
    void convertsOneBasedPageToSpringPageable() {
        var pageable = factory.create(2, 15);

        assertThat(pageable.getPageNumber()).isEqualTo(1);
        assertThat(pageable.getPageSize()).isEqualTo(15);
    }

    @Test
    void rejectsPageBelowOneAndPageSizeOutsideRange() {
        assertThatThrownBy(() -> factory.create(0, 20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> factory.create(1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> factory.create(1, 101)).isInstanceOf(IllegalArgumentException.class);
    }
}
