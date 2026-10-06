package uk.gov.hmcts.cp.addresslookup.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.annotation.Resource;

/**
 * Proves {@code OS_PLACES_CB_INITIAL_STATE} (the {@code initial-state} placeholder on the
 * {@code osPlaces} breaker in application.yaml) actually reaches the breaker the proxied
 * {@link OsPlacesRemoteCaller} runs through: DISABLED by default, letting every call through however
 * many fail, and CLOSED where a stack's Flux overlay sets it, tripping open as normal.
 *
 * <p>Needs a real application context, unlike {@link OsPlacesRemoteCallerTest}'s plain {@code new}
 * instance, since {@code @CircuitBreaker} only applies via the Spring AOP proxy. OS Places is a
 * {@link MockRestServiceServer}-bound {@link RestClient}, registered {@code @Primary} over the real
 * {@code osPlacesRestClient}, so each test pins exactly how many requests actually go out.
 *
 * <p>Each nested class gets its own context (and so its own breaker), and neither calls
 * {@code CircuitBreaker.reset()} - that always transitions to CLOSED, whatever the initial state.
 */
@SpringBootTest(properties = {
        // Trip after two recorded failures, rather than production's 15 calls into a 20-call window.
        "OS_PLACES_CB_WINDOW_SIZE=2",
        "OS_PLACES_CB_MIN_CALLS=2"
})
@Import(OsPlacesRemoteCallerCircuitBreakerTest.MockOsPlacesConfig.class)
class OsPlacesRemoteCallerCircuitBreakerTest {

    private static final String BASE_URL = "https://os-places.test";
    private static final String POSTCODE = "SW1A 1AA";
    private static final String API_KEY = "test-key";
    private static final String POSTCODE_URL = BASE_URL + "/search/places/v1/postcode?postcode=SW1A%201AA&key=test-key";
    private static final String CIRCUIT_BREAKER_NAME = "osPlaces";
    private static final int CALLS_TO_TRIP = 2;
    private static final int CALLS_WELL_PAST_THRESHOLD = 5;

    @Nested
    @TestPropertySource(properties = "OS_PLACES_CB_INITIAL_STATE=CLOSED")
    class WhenInitialStateIsClosed {

        // Fields live on each nested class, not the outer one: Spring injects the outer instance
        // from the outer class's own context, which would be the wrong breaker for the CLOSED case.
        @Resource
        private OsPlacesRemoteCaller remoteCaller;

        @Resource
        private MockRestServiceServer osPlacesServer;

        @Resource
        private CircuitBreakerRegistry circuitBreakerRegistry;

        @Test
        void breaker_starts_closed_and_opens_once_failures_reach_the_threshold() {
            final CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(CIRCUIT_BREAKER_NAME);
            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
            osPlacesServer.expect(ExpectedCount.times(CALLS_TO_TRIP), requestTo(POSTCODE_URL))
                    .andRespond(withServerError());

            for (int call = 0; call < CALLS_TO_TRIP; call++) {
                assertThatThrownBy(() -> remoteCaller.postcode(POSTCODE, API_KEY))
                        .isInstanceOf(HttpServerErrorException.class);
            }

            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
            assertThatThrownBy(() -> remoteCaller.postcode(POSTCODE, API_KEY))
                    .isInstanceOf(CallNotPermittedException.class);
            // Only the tripping calls reached OS Places - the open circuit refused the last one outright.
            osPlacesServer.verify();
        }
    }

    @Nested
    class WhenInitialStateIsNotSet {

        @Resource
        private OsPlacesRemoteCaller remoteCaller;

        @Resource
        private MockRestServiceServer osPlacesServer;

        @Resource
        private CircuitBreakerRegistry circuitBreakerRegistry;

        @Test
        void breaker_defaults_to_disabled_and_lets_every_failing_call_through() {
            final CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(CIRCUIT_BREAKER_NAME);
            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.DISABLED);
            osPlacesServer.expect(ExpectedCount.times(CALLS_WELL_PAST_THRESHOLD), requestTo(POSTCODE_URL))
                    .andRespond(withServerError());

            for (int call = 0; call < CALLS_WELL_PAST_THRESHOLD; call++) {
                assertThatThrownBy(() -> remoteCaller.postcode(POSTCODE, API_KEY))
                        .isInstanceOf(HttpServerErrorException.class);
            }

            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.DISABLED);
            // Every call reached OS Places - none were short-circuited.
            osPlacesServer.verify();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MockOsPlacesConfig {

        private final RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);

        @Bean
        MockRestServiceServer osPlacesServer() {
            return MockRestServiceServer.bindTo(builder).build();
        }

        // Takes the server as a parameter purely so it's bound to the builder before build().
        @Bean
        @Primary
        RestClient mockServerBoundOsPlacesRestClient(final MockRestServiceServer osPlacesServer) {
            return builder.build();
        }
    }
}
