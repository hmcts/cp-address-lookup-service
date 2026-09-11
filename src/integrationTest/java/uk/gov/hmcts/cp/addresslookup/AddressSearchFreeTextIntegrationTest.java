package uk.gov.hmcts.cp.addresslookup;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

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
 * End-to-end tests for {@code GET /addresses}: a real HTTP round-trip (embedded server on a random
 * port, {@link TestRestTemplate}) through the real controller, service, mapper and OS Places HTTP
 * client - only the OS Places backend itself is replaced with an in-process WireMock server, wired
 * in via {@code os-places.client.base-url}. Runs as part of {@code gradle build}/{@code check}.
 *
 * <p>OS Places stub responses are file-based WireMock mappings/fixtures under
 * {@code src/integrationTest/resources/wiremock/} ({@code mappings/address-search-*.json} +
 * {@code __files/address-search-*-body.json}), keyed on {@code query} (OS Places Find's own
 * param name), not {@code postcode}.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class AddressSearchFreeTextIntegrationTest {

    private static final MediaType MEDIA_TYPE =
            MediaType.parseMediaType("application/vnd.addresslookup-service.addresses+json");
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

    private <T> ResponseEntity<T> addressSearch(final UriComponentsBuilder query, final Class<T> responseType) {
        final URI uri = query.build().encode().toUri();
        final HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MEDIA_TYPE));
        return restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), responseType);
    }

    private static UriComponentsBuilder addresses() {
        return UriComponentsBuilder.fromPath("/addresses");
    }

    @Test
    void returns_candidates_mapped_from_the_os_places_dpa_record() {
        final ResponseEntity<AddressSearchResponse> response = addressSearch(
                addresses().queryParam("address", "10 Downing Street"), AddressSearchResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MEDIA_TYPE);
        assertThat(response.getBody().getResults()).hasSize(1);
        assertThat(response.getBody().getResults().get(0).getAddress1()).isEqualTo("10 Downing Street");
        assertThat(response.getBody().getResults().get(0).getAddress2()).isNull();
        assertThat(response.getBody().getResults().get(0).getAddress4()).isEqualTo("LONDON");
        assertThat(response.getBody().getResults().get(0).getPostcode()).isEqualTo("SW1A 1AA");
        assertThat(response.getBody().getResults().get(0).getUprn()).isEqualTo("10033544886");

        osPlaces.verify(WireMock.getRequestedFor(WireMock.urlPathEqualTo(OS_PLACES_PATH))
                .withQueryParam("query", WireMock.equalTo("10 Downing Street")));
    }

    @Test
    void nests_the_raw_dpa_record_when_include_dpa_is_requested() {
        final ResponseEntity<AddressSearchResponse> response = addressSearch(
                addresses().queryParam("address", "12 Downing Street").queryParam("include", "DPA"),
                AddressSearchResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getResults().get(0).getDpa())
                .containsEntry("UPRN", "10033544888")
                .containsEntry("BUILDING_NUMBER", "12");
    }

    @Test
    void forwards_a_postcode_suffixed_address_as_one_unsplit_query_string() {
        // Proves there is no server-side firstLine+postcode concatenation logic: the WireMock
        // mapping only matches if the entire literal string is forwarded as a single `query`
        // value. If any splitting/recombining logic existed, this would 404 upstream instead.
        final ResponseEntity<AddressSearchResponse> response = addressSearch(
                addresses().queryParam("address", "10 Downing Street SW1A 1AA"), AddressSearchResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getResults()).hasSize(1);

        osPlaces.verify(WireMock.getRequestedFor(WireMock.urlPathEqualTo(OS_PLACES_PATH))
                .withQueryParam("query", WireMock.equalTo("10 Downing Street SW1A 1AA")));
    }

    @Test
    void never_sends_the_api_key_back_in_any_response_header() {
        final ResponseEntity<AddressSearchResponse> response = addressSearch(
                addresses().queryParam("address", "10 Downing Street"), AddressSearchResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().get("key")).isNull();
    }

    @Test
    void returns_empty_results_when_os_places_has_no_matches() {
        final ResponseEntity<AddressSearchResponse> response = addressSearch(
                addresses().queryParam("address", "complete nonsense address"), AddressSearchResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MEDIA_TYPE);
        assertThat(response.getBody().getResults()).isEmpty();
    }

    @Test
    void returns_503_degraded_upstream_rate_limit_when_os_places_returns_429() {
        final ResponseEntity<DegradedResponse> response = addressSearch(
                addresses().queryParam("address", "rate limited street"), DegradedResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MEDIA_TYPE);
        assertThat(response.getBody().getDegraded()).isTrue();
        assertThat(response.getBody().getReason().getValue()).isEqualTo("upstream-rate-limit");
        assertThat(response.getBody().getRetryAfterSeconds()).isEqualTo(30);
    }

    @Test
    void returns_503_degraded_upstream_timeout_when_os_places_returns_5xx() {
        final ResponseEntity<DegradedResponse> response = addressSearch(
                addresses().queryParam("address", "server error street"), DegradedResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MEDIA_TYPE);
        assertThat(response.getBody().getDegraded()).isTrue();
        assertThat(response.getBody().getReason().getValue()).isEqualTo("upstream-timeout");
    }

    @Test
    void returns_503_degraded_upstream_auth_when_os_places_rejects_the_key() {
        final ResponseEntity<DegradedResponse> response = addressSearch(
                addresses().queryParam("address", "unauthorized street"), DegradedResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().getReason().getValue()).isEqualTo("upstream-auth");
    }

    @Test
    void rejects_missing_address_without_calling_os_places() {
        final ResponseEntity<ErrorResponse> response = addressSearch(addresses(), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MEDIA_TYPE);
        assertThat(response.getBody().getError()).isEqualTo("400");

        osPlaces.verify(0, WireMock.getRequestedFor(WireMock.urlPathEqualTo(OS_PLACES_PATH)));
    }

    @Test
    void rejects_unrecognised_query_parameters_without_calling_os_places() {
        final ResponseEntity<ErrorResponse> response = addressSearch(
                addresses().queryParam("address", "10 Downing Street").queryParam("bbox", "1,2,3,4"),
                ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MEDIA_TYPE);
        assertThat(response.getBody().getMessage()).isEqualTo("Unrecognised query parameter 'bbox'");

        osPlaces.verify(0, WireMock.getRequestedFor(WireMock.urlPathEqualTo(OS_PLACES_PATH))
                .withQueryParam("bbox", WireMock.equalTo("1,2,3,4")));
    }
}
