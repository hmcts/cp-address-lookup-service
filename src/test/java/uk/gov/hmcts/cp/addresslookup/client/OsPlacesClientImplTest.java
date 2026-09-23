package uk.gov.hmcts.cp.addresslookup.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import uk.gov.hmcts.cp.addresslookup.client.dto.OsPlacesResult;
import uk.gov.hmcts.cp.addresslookup.client.dto.OsPlacesSearchResponse;
import uk.gov.hmcts.cp.addresslookup.config.OsPlacesClientProperties;
import uk.gov.hmcts.cp.addresslookup.exception.DegradedModeException;
import uk.gov.hmcts.cp.openapi.model.al.DegradedReason;

/**
 * Since the actual OS Places HTTP call lives on {@link OsPlacesRemoteCaller} (see
 * {@link OsPlacesRemoteCallerTest} for that request-shape coverage), this class only tests
 * {@link OsPlacesClientImpl}'s own remaining job - mapping whatever the (mocked) caller throws to
 * the correct {@link DegradedReason}, operation-agnostically, plus the three operations'
 * delegation wiring.
 */
class OsPlacesClientImplTest {

    private static final String API_KEY = "test-key";

    private final OsPlacesRemoteCaller remoteCaller = mock(OsPlacesRemoteCaller.class);
    private final OsPlacesClientProperties properties =
            new OsPlacesClientProperties("https://os-places.test", API_KEY, 3000, 10_000, false);
    private final OsPlacesClientImpl client = new OsPlacesClientImpl(remoteCaller, properties);

    @Test
    void search_by_postcode_delegates_to_the_remote_caller_and_maps_dpa_records() {
        when(remoteCaller.postcode(eq("SW1A 1AA"), eq(API_KEY))).thenReturn(successResponse());

        final List<Map<String, Object>> results = client.searchByPostcode("SW1A 1AA");

        assertThat(results).hasSize(1);
        assertThat(results.get(0)).containsEntry("UPRN", "10033544886");
        verify(remoteCaller).postcode("SW1A 1AA", API_KEY);
    }

    @Test
    void search_by_address_delegates_to_the_remote_callers_find_method() {
        when(remoteCaller.find(eq("10 Downing Street"), eq(API_KEY))).thenReturn(successResponse());

        final List<Map<String, Object>> results = client.searchByAddress("10 Downing Street");

        assertThat(results).hasSize(1);
        verify(remoteCaller).find("10 Downing Street", API_KEY);
    }

    @Test
    void find_best_match_delegates_to_the_remote_callers_match_method() {
        when(remoteCaller.match(eq("10 Downing Street"), eq(new BigDecimal("0.7")), eq(API_KEY)))
                .thenReturn(successResponse());

        final List<Map<String, Object>> results = client.findBestMatch("10 Downing Street", new BigDecimal("0.7"));

        assertThat(results).hasSize(1);
        verify(remoteCaller).match("10 Downing Street", new BigDecimal("0.7"), API_KEY);
    }

    @Test
    void find_best_match_allows_a_null_min_match() {
        when(remoteCaller.match(eq("10 Downing Street"), isNull(), eq(API_KEY))).thenReturn(successResponse());

        client.findBestMatch("10 Downing Street", null);

        verify(remoteCaller).match("10 Downing Street", null, API_KEY);
    }

    @Test
    void returns_empty_list_when_results_is_null() {
        when(remoteCaller.postcode(eq("ZZ99 1AA"), eq(API_KEY))).thenReturn(new OsPlacesSearchResponse(null));

        assertThat(client.searchByPostcode("ZZ99 1AA")).isEmpty();
    }

    @Test
    void maps_429_to_upstream_rate_limit_with_retry_after() {
        stubFailure(HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests",
                headersWithRetryAfter(), new byte[0], StandardCharsets.UTF_8));

        assertThatThrownBy(() -> client.searchByPostcode("SW1A 1AA"))
                .isInstanceOf(DegradedModeException.class)
                .satisfies(ex -> {
                    final DegradedModeException degraded = (DegradedModeException) ex;
                    assertThat(degraded.getReason()).isEqualTo(DegradedReason.UPSTREAM_RATE_LIMIT);
                    assertThat(degraded.getRetryAfterSeconds()).isEqualTo(30);
                });
    }

    @Test
    void maps_401_to_upstream_auth() {
        stubFailure(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized",
                HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8));

        assertReason(DegradedReason.UPSTREAM_AUTH);
    }

    @Test
    void maps_403_to_upstream_auth() {
        stubFailure(HttpClientErrorException.create(HttpStatus.FORBIDDEN, "Forbidden",
                HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8));

        assertReason(DegradedReason.UPSTREAM_AUTH);
    }

    @Test
    void maps_500_to_upstream_server_error() {
        stubFailure(HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8));

        assertReason(DegradedReason.UPSTREAM_SERVER_ERROR);
    }

    @Test
    void maps_other_5xx_to_upstream_timeout() {
        stubFailure(HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable",
                HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8));

        assertReason(DegradedReason.UPSTREAM_TIMEOUT);
    }

    @Test
    void maps_other_4xx_to_upstream_contract() {
        stubFailure(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request",
                HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8));

        assertReason(DegradedReason.UPSTREAM_CONTRACT);
    }

    @Test
    void maps_malformed_json_to_upstream_contract() {
        stubFailure(new RestClientException("OS Places response could not be parsed"));

        assertReason(DegradedReason.UPSTREAM_CONTRACT);
    }

    @Test
    void maps_connection_failures_to_upstream_timeout() {
        stubFailure(new ResourceAccessException("Connection refused"));

        assertReason(DegradedReason.UPSTREAM_TIMEOUT);
    }

    @Test
    void maps_circuit_open_to_circuit_open() {
        final CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("osPlaces");
        stubFailure(CallNotPermittedException.createCallNotPermittedException(circuitBreaker));

        assertReason(DegradedReason.CIRCUIT_OPEN);
    }

    private void stubFailure(final RuntimeException cause) {
        when(remoteCaller.postcode(eq("SW1A 1AA"), eq(API_KEY))).thenThrow(cause);
    }

    private void assertReason(final DegradedReason expected) {
        assertThatThrownBy(() -> client.searchByPostcode("SW1A 1AA"))
                .isInstanceOf(DegradedModeException.class)
                .extracting(ex -> ((DegradedModeException) ex).getReason())
                .isEqualTo(expected);
    }

    private static HttpHeaders headersWithRetryAfter() {
        final HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.RETRY_AFTER, "30");
        return headers;
    }

    private static OsPlacesSearchResponse successResponse() {
        return new OsPlacesSearchResponse(List.of(new OsPlacesResult(Map.of(
                "UPRN", "10033544886",
                "BUILDING_NUMBER", "10",
                "THOROUGHFARE_NAME", "Downing Street",
                "POSTCODE", "SW1A 1AA"))));
    }
}
