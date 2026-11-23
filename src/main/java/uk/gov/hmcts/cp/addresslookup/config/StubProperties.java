package uk.gov.hmcts.cp.addresslookup.config;

import java.util.Locale;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SB-07: which environment's fixture folder the {@code stub} profile's embedded WireMock server
 * should load. No default - every environment activating the {@code stub} profile must say
 * explicitly which fixture set it wants; anything outside the allowlist fails startup immediately
 * with a clear message, rather than silently loading nothing or the wrong folder.
 */
@ConfigurationProperties(prefix = "os-places.stub")
public record StubProperties(String fixtureSet) {

    private static final Set<String> ALLOWED_FIXTURE_SETS = Set.of("ste", "dev", "nft");

    public StubProperties {
        final String normalised = fixtureSet == null ? null : fixtureSet.toLowerCase(Locale.ROOT);
        if (normalised == null || !ALLOWED_FIXTURE_SETS.contains(normalised)) {
            throw new IllegalStateException(
                    "os-places.stub.fixture-set must be one of " + ALLOWED_FIXTURE_SETS + ", got: " + fixtureSet);
        }
        fixtureSet = normalised;
    }
}
