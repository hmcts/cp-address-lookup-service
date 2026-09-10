package uk.gov.hmcts.cp.addresslookup.health;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import uk.gov.hmcts.cp.addresslookup.config.OsPlacesClientProperties;

class OsPlacesKeyHealthIndicatorTest {

    private static OsPlacesClientProperties properties(final String apiKey, final boolean keyRequired) {
        return new OsPlacesClientProperties("https://api.os.uk", apiKey, 3000, 10_000, keyRequired);
    }

    @Test
    void down_when_key_required_and_absent() {
        final Health health = new OsPlacesKeyHealthIndicator(properties("", true)).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void down_when_key_required_and_null() {
        final Health health = new OsPlacesKeyHealthIndicator(properties(null, true)).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void up_when_key_required_and_present() {
        final Health health = new OsPlacesKeyHealthIndicator(properties("a-real-key", true)).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void up_when_key_absent_but_not_required_on_this_tier() {
        final Health health = new OsPlacesKeyHealthIndicator(properties("", false)).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }
}
