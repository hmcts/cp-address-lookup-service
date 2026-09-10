package uk.gov.hmcts.cp.addresslookup;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.annotation.Resource;
import uk.gov.hmcts.cp.openapi.model.al.AddressSearchResponse;
import uk.gov.hmcts.cp.openapi.model.al.DegradedResponse;
import uk.gov.hmcts.cp.openapi.model.al.ErrorResponse;

/**
 * End-to-end tests for {@code GET /addresses/find}: a real HTTP round-trip (embedded server on a
 * random port, {@link TestRestTemplate}) through the real controller, service, mapper and OS
 * Places HTTP client - only the OS Places backend itself is replaced with an in-process WireMock
 * server. Runs as part of {@code gradle build}/{@code check}.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class AddressMatchIntegrationTest {

    private static final MediaType MEDIA_TYPE =
            MediaType.parseMediaType("application/vnd.addresslookup-service.addresses-find+json");
    private static final String OS_PLACES_PATH = "/search/places/v1/find";
    private static final String TEST_API_KEY = "test-api-key";

    @RegisterExtension
    static WireMockExtension osPlaces = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort().usingFilesUnderClasspath("wiremock"))
            .build();

    @DynamicPropertySource
    static void osPlacesClientProperties(final DynamicPropertyRegistry registry) {
        registry.add("os-places.client.base-url", osPlaces::baseUrl);
        registry.add("os-places.client.api-key", () -> TEST_API_KEY);
    }

    @Resource
    private TestRestTemplate restTemplate;

    private <T> ResponseEntity<T> findAddress(final UriComponentsBuilder query, final Class<T> responseType) {
        final URI uri = query.build().encode().toUri();
        final HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MEDIA_TYPE));
        return restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), responseType);
    }

    private static UriComponentsBuilder addressesFind() {
        return UriComponentsBuilder.fromPath("/addresses/find");
    }

    @Test
    void returns_at_most_one_candidate_with_its_match_score() {
        final ResponseEntity<AddressSearchResponse> response = findAddress(
                addressesFind().queryParam("address", "10 Downing Street").queryParam("minMatch", "0.7"),
                AddressSearchResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MEDIA_TYPE);
        assertThat(response.getBody().getResults()).hasSize(1);
        assertThat(response.getBody().getResults().get(0).getUprn()).isEqualTo("10033544886");
        assertThat(response.getBody().getResults().get(0).getMatch()).isEqualByComparingTo(new BigDecimal("0.95"));

        osPlaces.verify(WireMock.getRequestedFor(WireMock.urlPathEqualTo(OS_PLACES_PATH))
                .withQueryParam("maxresults", WireMock.equalTo("1"))
                .withQueryParam("minmatch", WireMock.equalTo("0.7")));
    }

    @Test
    void returns_200_with_empty_results_for_a_nonsense_address_not_a_degraded_response() {
        final ResponseEntity<AddressSearchResponse> response = findAddress(
                addressesFind().queryParam("address", "complete gibberish nonsense").queryParam("minMatch", "0.7"),
                AddressSearchResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MEDIA_TYPE);
        assertThat(response.getBody().getResults()).isEmpty();
    }

    @Test
    void returns_503_degraded_upstream_rate_limit_when_os_places_returns_429() {
        final ResponseEntity<DegradedResponse> response = findAddress(
                addressesFind().queryParam("address", "rate limited match street"), DegradedResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MEDIA_TYPE);
        assertThat(response.getBody().getDegraded()).isTrue();
        assertThat(response.getBody().getReason().getValue()).isEqualTo("upstream-rate-limit");
        assertThat(response.getBody().getRetryAfterSeconds()).isEqualTo(30);
    }

    @Test
    void rejects_missing_address_without_calling_os_places() {
        final ResponseEntity<ErrorResponse> response = findAddress(addressesFind(), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MEDIA_TYPE);
        assertThat(response.getBody().getError()).isEqualTo("400");

        osPlaces.verify(0, WireMock.getRequestedFor(WireMock.urlPathEqualTo(OS_PLACES_PATH)));
    }

    @Test
    void rejects_min_match_below_the_floor_without_calling_os_places() {
        final ResponseEntity<ErrorResponse> response = findAddress(
                addressesFind().queryParam("address", "10 Downing Street").queryParam("minMatch", "0.05"),
                ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        osPlaces.verify(0, WireMock.getRequestedFor(WireMock.urlPathEqualTo(OS_PLACES_PATH)));
    }

    @Test
    void rejects_unrecognised_query_parameters_without_calling_os_places() {
        final ResponseEntity<ErrorResponse> response = findAddress(
                addressesFind().queryParam("address", "10 Downing Street").queryParam("matchprecision", "1"),
                ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MEDIA_TYPE);
        assertThat(response.getBody().getMessage()).isEqualTo("Unrecognised query parameter 'matchprecision'");

        osPlaces.verify(0, WireMock.getRequestedFor(WireMock.urlPathEqualTo(OS_PLACES_PATH))
                .withQueryParam("matchprecision", WireMock.equalTo("1")));
    }

    @Test
    void never_sends_the_api_key_back_in_any_response_header() {
        final ResponseEntity<AddressSearchResponse> response = findAddress(
                addressesFind().queryParam("address", "10 Downing Street").queryParam("minMatch", "0.7"),
                AddressSearchResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().get("key")).isNull();
    }
}
