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
        boolean keyRequired,
        String postcodePath,
        String findPath
) {

    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 3000;
    private static final int DEFAULT_READ_TIMEOUT_MS = 10_000;

    // No Java-side default for postcodePath/findPath (Sonar: URIs should not be hardcoded) -
    // application.yaml's own ${...:default} syntax is the single source of truth for those
    // literals; callers that bypass Spring binding (tests) must pass explicit values.
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
                + ", keyRequired=" + keyRequired
                + ", postcodePath=" + postcodePath
                + ", findPath=" + findPath + "]";
    }
}
