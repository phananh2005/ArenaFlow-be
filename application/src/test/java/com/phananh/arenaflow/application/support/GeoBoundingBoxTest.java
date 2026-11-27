package com.phananh.arenaflow.application.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class GeoBoundingBoxTest {

    @Test
    void boxAroundPointCoversRadiusOnBothAxes() {
        GeoBoundingBox box = GeoBoundingBox.around(10.0, 106.0, 11.132);

        assertThat(box.minLat()).isCloseTo(9.9, within(1e-9));
        assertThat(box.maxLat()).isCloseTo(10.1, within(1e-9));
        double longitudeDelta = 11.132 / (111.32 * Math.cos(Math.toRadians(10.0)));
        assertThat(box.minLng()).isCloseTo(106.0 - longitudeDelta, within(1e-9));
        assertThat(box.maxLng()).isCloseTo(106.0 + longitudeDelta, within(1e-9));
    }
}
