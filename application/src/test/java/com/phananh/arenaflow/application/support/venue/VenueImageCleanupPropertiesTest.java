package com.phananh.arenaflow.application.support.venue;

import com.phananh.arenaflow.application.support.storage.CloudinaryProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VenueImageCleanupPropertiesTest {

    private final VenueImageCleanupProperties defaults =
            new VenueImageCleanupProperties(null, null, null, null, null, null, null);

    @Test
    void allowsEnablingWithConfiguredStorageAndDedicatedFolder() {
        assertThatCode(() -> defaults.requireSafeToEnable(cloudinary("arenaflow-dev"))).doesNotThrowAnyException();
    }

    @Test
    void refusesWhenStorageIsNotConfiguredOrFolderIsSharedDefault() {
        assertThatThrownBy(() -> defaults.requireSafeToEnable(cloudinary("")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> defaults.requireSafeToEnable(new CloudinaryProperties("cloud", "key", "", "arenaflow-dev")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> defaults.requireSafeToEnable(cloudinary(" arenaflow ")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refusesWhenGraceDoesNotExceedConfirmWindowPlusSignatureLifetime() {
        VenueImageCleanupProperties tooShort = new VenueImageCleanupProperties(Duration.ofHours(7), Duration.ofHours(6),
                null, null, null, null, null);

        assertThatThrownBy(() -> tooShort.requireSafeToEnable(cloudinary("arenaflow-dev")))
                .isInstanceOf(IllegalStateException.class);
    }

    private static CloudinaryProperties cloudinary(String folder) {
        return new CloudinaryProperties("cloud", "key", "secret", folder);
    }
}
