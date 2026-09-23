package uk.gov.hmcts.cp.addresslookup;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.util.UriComponentsBuilder;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.annotation.Resource;
import uk.gov.hmcts.cp.addresslookup.config.CacheConfig;
import uk.gov.hmcts.cp.openapi.model.al.AddressSearchResponse;
import uk.gov.hmcts.cp.openapi.model.al.DegradedReason;
import uk.gov.hmcts.cp.openapi.model.al.DegradedResponse;

/**
 * SB-06: proves the {@code osPlaces} circuit breaker's actual open/half-open/closed behaviour end
 * to end - not covered by {@link uk.gov.hmcts.cp.addresslookup.client.OsPlacesClientImplTest}
 * (which only tests the exception-to-{@link DegradedReason} mapping against a mocked collaborator)
 * since the breaker only really exists once the {@code @CircuitBreaker} annotation is applied via
 * Spring AOP in a real application context. No {@code @TimeLimiter} (see
 * {@link uk.gov.hmcts.cp.addresslookup.client.OsPlacesRemoteCaller}'s Javadoc for why) - the 10s
 * budget is just the plain {@code os-places.client.read-timeout-ms} transport timeout.
 *
 * <p>Overrides the breaker's thresholds to small values via {@code @DynamicPropertySource} so this
 * trips/recovers quickly and deterministically, rather than needing 15 real failing calls.
 * Stubs OS Places programmatically (not file-based fixtures like the other integration tests in
 * this package) since this test needs to change OS Places' behaviour mid-test - failing, then
 * recovering - which a static mapping file can't express.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class CircuitBreakerIntegrationTest {

    private static final MediaType MEDIA_TYPE =
            MediaType.parseMediaType("application/vnd.addresslookup-service.addresses-postcode+json");
    private static final String OS_PLACES_PATH = "/search/places/v1/postcode";
    private static final String POSTCODE = "SW1A 1AA";
    private static final long HALF_OPEN_WAIT_MS = 1_500L;

    @RegisterExtension
    static WireMockExtension osPlaces = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void osPlacesClientProperties(final DynamicPropertyRegistry registry) {
        registry.add("os-places.client.base-url", osPlaces::baseUrl);
        registry.add("os-places.client.api-key", () -> "test-api-key");
        registry.add("resilience4j.circuitbreaker.instances.osPlaces.sliding-window-size", () -> 2);
        registry.add("resilience4j.circuitbreaker.instances.osPlaces.minimum-number-of-calls", () -> 2);
        registry.add("resilience4j.circuitbreaker.instances.osPlaces.wait-duration-in-open-state", () -> "1s");
        registry.add("resilience4j.circuitbreaker.instances.osPlaces.permitted-number-of-calls-in-half-open-state",
                () -> 1);
    }

    @Resource
    private TestRestTemplate restTemplate;

    @Resource
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Resource
    private CacheManager cacheManager;

    @BeforeEach
    void resetState() {
        cacheManager.getCache(CacheConfig.ADDRESS_LOOKUP_CACHE).clear();
        circuitBreakerRegistry.circuitBreaker("osPlaces").reset();
        osPlaces.resetAll();
    }

    @Test
    void open_circuit_answers_degraded_instantly_then_closes_again_after_recovery() throws InterruptedException {
        osPlaces.stubFor(get(urlPathEqualTo(OS_PLACES_PATH)).willReturn(aResponse().withStatus(500)));

        // minimum-number-of-calls=2, failure-rate-threshold defaults to 50 - two failing calls is
        // enough for the breaker to open.
        final ResponseEntity<DegradedResponse> first = postcodeSearch(DegradedResponse.class);
        final ResponseEntity<DegradedResponse> second = postcodeSearch(DegradedResponse.class);
        assertThat(first.getBody().getReason()).isEqualTo(DegradedReason.UPSTREAM_SERVER_ERROR);
        assertThat(second.getBody().getReason()).isEqualTo(DegradedReason.UPSTREAM_SERVER_ERROR);
        assertThat(circuitBreakerRegistry.circuitBreaker("osPlaces").getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // Next call: instant degraded, no OS Places request made at all.
        osPlaces.resetRequests();
        final ResponseEntity<DegradedResponse> whileOpen = postcodeSearch(DegradedResponse.class);
        assertThat(whileOpen.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(whileOpen.getBody().getReason()).isEqualTo(DegradedReason.CIRCUIT_OPEN);
        osPlaces.verify(0, getRequestedFor(urlPathEqualTo(OS_PLACES_PATH)));

        // Wait past wait-duration-in-open-state (1s - automatic-transition-from-open-to-half-open
        // moves it to HALF_OPEN on its own), reconfigure OS Places to succeed, and prove one
        // successful call in the half-open trial closes the circuit again.
        osPlaces.resetAll();
        osPlaces.stubFor(get(urlPathEqualTo(OS_PLACES_PATH)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"results":[{"DPA":{"UPRN":"10033544886","BUILDING_NUMBER":"10",
                        "THOROUGHFARE_NAME":"Downing Street","POSTCODE":"SW1A 1AA"}}]}
                        """)));
        Thread.sleep(HALF_OPEN_WAIT_MS);

        final ResponseEntity<AddressSearchResponse> recovered = postcodeSearch(AddressSearchResponse.class);
        assertThat(recovered.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(recovered.getBody().getResults()).hasSize(1);
        assertThat(circuitBreakerRegistry.circuitBreaker("osPlaces").getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void a_slow_but_successful_response_is_not_cut_off_early() {
        // Documents the actual Option 2 trade-off: with no @TimeLimiter, nothing races a separate
        // deadline against the call - a response that's slow but still arrives within
        // read-timeout-ms succeeds normally, waiting out the real delay in full, rather than being
        // cut off early and deterministically the way a TimeLimiter budget would.
        osPlaces.stubFor(get(urlPathEqualTo(OS_PLACES_PATH)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withFixedDelay(1_000)
                .withBody("""
                        {"results":[{"DPA":{"UPRN":"10033544886","BUILDING_NUMBER":"10",
                        "THOROUGHFARE_NAME":"Downing Street","POSTCODE":"SW1A 1AA"}}]}
                        """)));

        final long start = System.currentTimeMillis();
        final ResponseEntity<AddressSearchResponse> response = postcodeSearch(AddressSearchResponse.class);
        final long elapsedMs = System.currentTimeMillis() - start;

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getResults()).hasSize(1);
        assertThat(elapsedMs).isGreaterThanOrEqualTo(1_000L);
        assertThat(circuitBreakerRegistry.circuitBreaker("osPlaces").getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    private <T> ResponseEntity<T> postcodeSearch(final Class<T> responseType) {
        final URI uri = UriComponentsBuilder.fromPath("/addresses/postcode")
                .queryParam("postcode", POSTCODE)
                .build().encode().toUri();
        final HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MEDIA_TYPE));
        return restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), responseType);
    }
}
