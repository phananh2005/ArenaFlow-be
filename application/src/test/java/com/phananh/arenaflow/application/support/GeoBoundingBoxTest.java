package com.phananh.arenaflow.application.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class GeoBoundingBoxTest {

    @Test
    void boxMatchesDegreeApproximationAroundPoint() {
        GeoBoundingBox box = GeoBoundingBox.of(10.0, 106.0, 3.0);
        double latDelta = 3.0 / 111.32;
        double lngDelta = 3.0 / (111.32 * Math.cos(Math.toRadians(10.0)));

        assertThat(box.minLat()).isCloseTo(10.0 - latDelta, within(1e-12));
        assertThat(box.maxLat()).isCloseTo(10.0 + latDelta, within(1e-12));
        assertThat(box.minLng()).isCloseTo(106.0 - lngDelta, within(1e-12));
        assertThat(box.maxLng()).isCloseTo(106.0 + lngDelta, within(1e-12));
    }
}
