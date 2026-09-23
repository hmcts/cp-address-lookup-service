package uk.gov.hmcts.cp.addresslookup.client;

import java.math.BigDecimal;
import java.util.Objects;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import uk.gov.hmcts.cp.addresslookup.client.dto.OsPlacesSearchResponse;

/**
 * SB-06 (revised): owns the actual outbound HTTP call to OS Places, decorated with Resilience4j's
 * {@code @CircuitBreaker}. Split out from {@link OsPlacesClientImpl} because Spring AOP only
 * intercepts calls made through the proxied bean reference - a self-invoked method on the same
 * class would bypass the interceptor entirely.
 *
 * <p>No {@code @TimeLimiter} here: it requires a {@code CompletionStage}-returning method, which
 * would mean an async layer (executor, virtual threads, {@code CompletableFuture} unwrapping) purely
 * to satisfy that constraint. The 10s budget is instead just {@code os-places.client.read-timeout-ms}
 * on the underlying {@link RestClient} - a plain transport-level timeout, not something
 * Resilience4j itself enforces or can report on. Deliberate trade-off: a genuinely slow-but-alive
 * OS Places response is no longer cut off early and deterministically at 10s (every caller waits
 * out however long OS Places actually takes, up to the transport timeout) - traded for not needing
 * any async/virtual-thread machinery at all. Repeated hard failures (5xx, connection failures,
 * timeouts) still trip {@code @CircuitBreaker} exactly as before.
 *
 * <p>When the circuit is open, {@code CallNotPermittedException} is thrown directly and
 * synchronously by the AOP proxy - the method body here never runs, no OS Places call attempted.
 */
@Component
public class OsPlacesRemoteCaller {

    private static final String POSTCODE_PATH = "/search/places/v1/postcode";
    private static final String FIND_PATH = "/search/places/v1/find";
    private static final int MATCH_MAX_RESULTS = 1;
    // Matches resilience4j.circuitbreaker.instances.osPlaces in application.yaml.
    private static final String CIRCUIT_BREAKER_NAME = "osPlaces";

    private final RestClient restClient;

    public OsPlacesRemoteCaller(final RestClient osPlacesRestClient) {
        this.restClient = Objects.requireNonNull(osPlacesRestClient, "osPlacesRestClient");
    }

    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME)
    public OsPlacesSearchResponse postcode(final String postcode, final String apiKey) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder.path(POSTCODE_PATH)
                        .queryParam("postcode", postcode)
                        .queryParam("key", apiKey)
                        .build())
                .retrieve()
                .body(OsPlacesSearchResponse.class);
    }

    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME)
    public OsPlacesSearchResponse find(final String address, final String apiKey) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder.path(FIND_PATH)
                        .queryParam("query", address)
                        .queryParam("key", apiKey)
                        .build())
                .retrieve()
                .body(OsPlacesSearchResponse.class);
    }

    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME)
    public OsPlacesSearchResponse match(final String address, final BigDecimal minMatch, final String apiKey) {
        return restClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path(FIND_PATH)
                            .queryParam("query", address)
                            .queryParam("maxresults", MATCH_MAX_RESULTS);
                    if (minMatch != null) {
                        uriBuilder.queryParam("minmatch", minMatch);
                    }
                    return uriBuilder.queryParam("key", apiKey).build();
                })
                .retrieve()
                .body(OsPlacesSearchResponse.class);
    }
}
