package uk.gov.hmcts.cp.addresslookup.client;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.slf4j.Slf4j;
import uk.gov.hmcts.cp.addresslookup.client.dto.OsPlacesResult;
import uk.gov.hmcts.cp.addresslookup.client.dto.OsPlacesSearchResponse;
import uk.gov.hmcts.cp.addresslookup.config.OsPlacesClientProperties;
import uk.gov.hmcts.cp.addresslookup.exception.DegradedModeException;
import uk.gov.hmcts.cp.openapi.model.al.DegradedReason;

/**
 * Thin synchronous adapter over {@link OsPlacesRemoteCaller} - maps whatever failed the call (an
 * OS Places HTTP error, or an open {@code @CircuitBreaker}) to the same {@link DegradedModeException}
 * contract, so nothing above this class (the service layer, controllers, {@code @Cacheable})
 * needs to know Resilience4j exists at all.
 */
@Slf4j
@Component
public class OsPlacesClientImpl implements OsPlacesClient {

    private final OsPlacesRemoteCaller remoteCaller;
    private final String apiKey;

    public OsPlacesClientImpl(final OsPlacesRemoteCaller remoteCaller, final OsPlacesClientProperties properties) {
        this.remoteCaller = Objects.requireNonNull(remoteCaller, "remoteCaller");
        this.apiKey = Objects.requireNonNull(properties, "properties").apiKey();
    }

    @Override
    public List<Map<String, Object>> searchByPostcode(final String postcode) {
        return execute(() -> remoteCaller.postcode(postcode, apiKey));
    }

    @Override
    public List<Map<String, Object>> searchByAddress(final String address) {
        return execute(() -> remoteCaller.find(address, apiKey));
    }

    @Override
    public List<Map<String, Object>> findBestMatch(final String address, final BigDecimal minMatch) {
        return execute(() -> remoteCaller.match(address, minMatch, apiKey));
    }

    // Deliberately broad: mapToDegraded's whole job is to classify every kind of RuntimeException
    // this call can throw (OS Places HTTP errors, connection failures, an open circuit breaker)
    // into the right DegradedReason - narrowing the catch type here would defeat that.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private List<Map<String, Object>> execute(final Supplier<OsPlacesSearchResponse> call) {
        final OsPlacesSearchResponse response;
        try {
            response = call.get();
        } catch (final RuntimeException ex) {
            throw mapToDegraded(ex);
        }
        return toResults(response);
    }

    private static List<Map<String, Object>> toResults(final OsPlacesSearchResponse response) {
        final List<OsPlacesResult> results = response == null ? null : response.results();
        return results == null ? List.of() : results.stream()
                .map(OsPlacesResult::dpa)
                .filter(Objects::nonNull)
                .toList();
    }

    private static DegradedModeException mapToDegraded(final RuntimeException cause) {
        return switch (cause) {
            case HttpClientErrorException.TooManyRequests ex ->
                    degraded(DegradedReason.UPSTREAM_RATE_LIMIT, retryAfterSeconds(ex), "OS Places rate limit exceeded", ex);
            case HttpClientErrorException.Unauthorized ex ->
                    degraded(DegradedReason.UPSTREAM_AUTH, null, "OS Places rejected the configured API key", ex);
            case HttpClientErrorException.Forbidden ex ->
                    degraded(DegradedReason.UPSTREAM_AUTH, null, "OS Places rejected the configured API key", ex);
            case HttpServerErrorException.InternalServerError ex ->
                    degraded(DegradedReason.UPSTREAM_SERVER_ERROR, null, "OS Places returned a 500 Internal Server Error", ex);
            case HttpServerErrorException ex ->
                    degraded(DegradedReason.UPSTREAM_TIMEOUT, null, "OS Places returned a server error", ex);
            case HttpClientErrorException ex ->
                    degraded(DegradedReason.UPSTREAM_CONTRACT, null, "OS Places rejected the request unexpectedly", ex);
            // The circuit breaker refused the call outright - instant, no OS Places call made.
            case CallNotPermittedException ex ->
                    degraded(DegradedReason.CIRCUIT_OPEN, null, "OS Places circuit breaker is open", ex);
            case ResourceAccessException ex ->
                    degraded(DegradedReason.UPSTREAM_TIMEOUT, null, "OS Places did not respond in time", ex);
            case RestClientException ex ->
                    degraded(DegradedReason.UPSTREAM_CONTRACT, null, "OS Places response could not be read", ex);
            default ->
                    degraded(DegradedReason.UPSTREAM_CONTRACT, null, "OS Places call failed unexpectedly", cause);
        };
    }

    private static Integer retryAfterSeconds(final HttpClientErrorException ex) {
        Integer seconds = null;
        final HttpHeaders headers = ex.getResponseHeaders();
        final String retryAfter = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (retryAfter != null) {
            try {
                seconds = Integer.valueOf(retryAfter.trim());
            } catch (final NumberFormatException ignored) {
                // seconds stays null (already initialised above)
            }
        }
        return seconds;
    }

    private static DegradedModeException degraded(final DegradedReason reason, final Integer retryAfterSeconds,
            final String message, final Throwable cause) {
        log.warn("OS Places call degraded: reason={} message={}", reason, message, cause);
        return new DegradedModeException(reason, retryAfterSeconds, message, cause);
    }
}
