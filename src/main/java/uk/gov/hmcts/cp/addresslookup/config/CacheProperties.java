package uk.gov.hmcts.cp.addresslookup.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalised in-process response cache configuration (SB-05). TTL starts conservative (10
 * minutes) pending a licensing answer on how long OS Places results may be retained - see the
 * design doc's cross-cutting concerns table.
 */
@ConfigurationProperties(prefix = "address-lookup.cache")
public record CacheProperties(int ttlMinutes, long maximumSize) {

    private static final int DEFAULT_TTL_MINUTES = 10;
    private static final long DEFAULT_MAXIMUM_SIZE = 10_000;

    public CacheProperties {
        if (ttlMinutes <= 0) {
            ttlMinutes = DEFAULT_TTL_MINUTES;
        }
        if (maximumSize <= 0) {
            maximumSize = DEFAULT_MAXIMUM_SIZE;
        }
    }
}
