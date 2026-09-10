package uk.gov.hmcts.cp.addresslookup.health;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import uk.gov.hmcts.cp.addresslookup.config.OsPlacesClientProperties;

/**
 * Readiness signal for the configured OS Places API key. On OS-backed tiers
 * ({@code os-places.client.key-required=true}, set only for SIT/PRP/PRD) an absent key marks the
 * pod not-ready - it stays alive and is simply pulled out of traffic until a key is provisioned,
 * with no restart or redeploy needed. Fixture tiers (STE/DEV/NFT) have no key requirement, so a
 * blank key there is normal and does not affect readiness.
 *
 * <p>This checks configuration presence only, never calls OS Places - a live upstream call in a
 * readiness probe would cost money on every pod restart and produce false negatives on transient
 * OS blips.
 */
@Component("osPlacesKey")
public class OsPlacesKeyHealthIndicator implements HealthIndicator {

    private final OsPlacesClientProperties properties;

    public OsPlacesKeyHealthIndicator(final OsPlacesClientProperties properties) {
        this.properties = properties;
    }

    @Override
    public Health health() {
        final boolean keyPresent = properties.apiKey() != null && !properties.apiKey().isBlank();
        final Health.Builder builder;
        if (properties.keyRequired() && !keyPresent) {
            builder = Health.down().withDetail("reason", "os-places-key is required on this tier but is absent");
        } else {
            builder = Health.up();
        }
        return builder.build();
    }
}
