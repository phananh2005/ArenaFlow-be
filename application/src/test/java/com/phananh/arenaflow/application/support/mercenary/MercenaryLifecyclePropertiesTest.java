package com.phananh.arenaflow.application.support.mercenary;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MercenaryLifecyclePropertiesTest {

    @Test
    void bindsKebabCaseKeys() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(
                "arenaflow.mercenary.lifecycle.batch-size", "200",
                "arenaflow.mercenary.lifecycle.max-batches-per-run", "50",
                "arenaflow.mercenary.lifecycle.cancel-notice-lookback-days", "7")));

        MercenaryLifecycleProperties properties = binder.bind("arenaflow.mercenary.lifecycle",
                MercenaryLifecycleProperties.class).get();

        assertThat(properties).isEqualTo(new MercenaryLifecycleProperties(200, 50, 7));
    }
}
