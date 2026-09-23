package uk.gov.hmcts.cp.addresslookup.client;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import uk.gov.hmcts.cp.addresslookup.client.dto.OsPlacesSearchResponse;

/**
 * SB-06: owns the actual outbound HTTP call to OS Places, decorated with Resilience4j's
 * {@code @CircuitBreaker}/{@code @TimeLimiter}. Both only apply via Spring AOP to a method
 * returning {@code CompletableFuture}/{@code CompletionStage} - that's why this is split out from
 * {@link OsPlacesClientImpl} rather than annotated there directly: {@link OsPlacesClientImpl}
 * (and everything above it - the service layer, controllers, {@code @Cacheable}) stays completely
 * synchronous and unaware that OS Places calls run through a resilience layer at all.
 *
 * <p>When the circuit is open, the {@code @CircuitBreaker} aspect never invokes this class' code -
 * it returns an already-exceptionally-completed future instead, so callers see a
 * {@link io.github.resilience4j.circuitbreaker.CallNotPermittedException} the instant they inspect
 * the future, with no OS Places call attempted.
 */
@Component
public class OsPlacesRemoteCaller {

    private static final String POSTCODE_PATH = "/search/places/v1/postcode";
    private static final String FIND_PATH = "/search/places/v1/find";
    private static final int MATCH_MAX_RESULTS = 1;
    // Matches resilience4j.circuitbreaker/timelimiter.instances.osPlaces in application.yaml.
    private static final String CIRCUIT_BREAKER_NAME = "osPlaces";

    private final RestClient restClient;
    private final ExecutorService executor;

    public OsPlacesRemoteCaller(final RestClient osPlacesRestClient, final ExecutorService osPlacesExecutor) {
        this.restClient = Objects.requireNonNull(osPlacesRestClient, "osPlacesRestClient");
        this.executor = Objects.requireNonNull(osPlacesExecutor, "osPlacesExecutor");
    }

    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME)
    @TimeLimiter(name = CIRCUIT_BREAKER_NAME)
    public CompletableFuture<OsPlacesSearchResponse> postcode(final String postcode, final String apiKey) {
        return CompletableFuture.supplyAsync(() -> restClient.get()
                .uri(uriBuilder -> uriBuilder.path(POSTCODE_PATH)
                        .queryParam("postcode", postcode)
                        .queryParam("key", apiKey)
                        .build())
                .retrieve()
                .body(OsPlacesSearchResponse.class), executor);
    }

    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME)
    @TimeLimiter(name = CIRCUIT_BREAKER_NAME)
    public CompletableFuture<OsPlacesSearchResponse> find(final String address, final String apiKey) {
        return CompletableFuture.supplyAsync(() -> restClient.get()
                .uri(uriBuilder -> uriBuilder.path(FIND_PATH)
                        .queryParam("query", address)
                        .queryParam("key", apiKey)
                        .build())
                .retrieve()
                .body(OsPlacesSearchResponse.class), executor);
    }

    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME)
    @TimeLimiter(name = CIRCUIT_BREAKER_NAME)
    public CompletableFuture<OsPlacesSearchResponse> match(final String address, final BigDecimal minMatch,
            final String apiKey) {
        return CompletableFuture.supplyAsync(() -> restClient.get()
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
                .body(OsPlacesSearchResponse.class), executor);
    }
}
