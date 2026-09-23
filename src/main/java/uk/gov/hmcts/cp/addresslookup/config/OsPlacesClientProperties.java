package uk.gov.hmcts.cp.addresslookup.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalised OS Places client configuration. {@code apiKey} is sourced from an environment
 * variable/secret (see application.yaml) and must never be logged.
 */
@ConfigurationProperties(prefix = "os-places.client")
public record OsPlacesClientProperties(
        String baseUrl,
        String apiKey,
        int connectTimeoutMs,
        int readTimeoutMs,
        boolean keyRequired
) {

    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 3000;
    // SB-06: Resilience4j's TimeLimiter is now the authoritative 10s budget (see
    // resilience4j.timelimiter.instances.osPlaces in application.yaml) - this stays a slightly
    // larger transport-level safety net underneath it, not the enforced budget itself.
    private static final int DEFAULT_READ_TIMEOUT_MS = 12_000;

    public OsPlacesClientProperties {
        if (connectTimeoutMs <= 0) {
            connectTimeoutMs = DEFAULT_CONNECT_TIMEOUT_MS;
        }
        if (readTimeoutMs <= 0) {
            readTimeoutMs = DEFAULT_READ_TIMEOUT_MS;
        }
    }

    public Duration connectTimeout() {
        return Duration.ofMillis(connectTimeoutMs);
    }

    public Duration readTimeout() {
        return Duration.ofMillis(readTimeoutMs);
    }

    /**
     * Overridden so the API key is never accidentally exposed via logging, actuator, or debugging.
     */
    @Override
    public String toString() {
        return "OsPlacesClientProperties[baseUrl=" + baseUrl
                + ", apiKey=****, connectTimeoutMs=" + connectTimeoutMs
                + ", readTimeoutMs=" + readTimeoutMs
                + ", keyRequired=" + keyRequired + "]";
    }
}
