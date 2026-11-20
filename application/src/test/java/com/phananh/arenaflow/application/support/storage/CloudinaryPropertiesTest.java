package com.phananh.arenaflow.application.support.storage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CloudinaryPropertiesTest {

    @Test
    void toStringMasksApiSecretButKeepsNonSecretFields() {
        CloudinaryProperties properties =
                new CloudinaryProperties("demo-cloud", "public-key", "super-secret-value", "arenaflow-dev");

        assertThat(properties.toString())
                .doesNotContain("super-secret-value")
                .contains("cloudName=demo-cloud", "folder=arenaflow-dev")
                .contains("apiKey=public-key", "apiSecret=***"); // allow-secret: giá trị giả trong test
    }

    @Test
    void toStringShowsWhenApiSecretIsMissing() {
        assertThat(new CloudinaryProperties("demo-cloud", "public-key", "", "arenaflow-dev").toString())
                .contains("apiSecret=<empty>");
    }
}
