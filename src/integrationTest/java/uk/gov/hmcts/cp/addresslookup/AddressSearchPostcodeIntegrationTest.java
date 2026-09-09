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
 * End-to-end tests for {@code GET /addresses/postcode}: a real HTTP round-trip (embedded server on
 * a random port, {@link TestRestTemplate}) through the real controller, service, mapper and OS
 * Places HTTP client - only the OS Places backend itself is replaced with an in-process WireMock
 * server, wired in via {@code os-places.client.base-url}. Runs as part of {@code gradle build}/
 * {@code check} - no external services, Docker, or MockMvc simulation required.
 *
 * <p>OS Places stub responses are file-based WireMock mappings/fixtures under
 * {@code src/integrationTest/resources/wiremock/} ({@code mappings/*.json} + {@code __files/*.json})
 * rather than programmatic {@code stubFor(...)} calls - each mapping keys off a distinct postcode
 * so every test scenario has its own fixture, editable independently of the test code.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class AddressSearchPostcodeIntegrationTest {

    private static final MediaType MEDIA_TYPE =
            MediaType.parseMediaType("application/vnd.addresslookup-service.addresses-postcode+json");
    private static final String OS_PLACES_PATH = "/search/places/v1/postcode";
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

    private <T> ResponseEntity<T> postcodeSearch(final UriComponentsBuilder query, final Class<T> responseType) {
        final URI uri = query.build().encode().toUri();
        final HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MEDIA_TYPE));
        return restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), responseType);
    }

    private static UriComponentsBuilder addressesPostcode() {
        return UriComponentsBuilder.fromPath("/addresses/postcode");
    }

    @Test
    void returns_candidates_mapped_from_the_os_places_dpa_record() {
        final ResponseEntity<AddressSearchResponse> response = postcodeSearch(
                addressesPostcode().queryParam("postcode", "SW1A 1AA"), AddressSearchResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MEDIA_TYPE);
        assertThat(response.getBody().getResults()).hasSize(1);
        assertThat(response.getBody().getResults().get(0).getAddress1()).isEqualTo("10");
        assertThat(response.getBody().getResults().get(0).getAddress2()).isEqualTo("Downing Street");
        assertThat(response.getBody().getResults().get(0).getAddress3()).isEqualTo("LONDON");
        assertThat(response.getBody().getResults().get(0).getPostcode()).isEqualTo("SW1A 1AA");
        assertThat(response.getBody().getResults().get(0).getUprn()).isEqualTo("10033544886");
        // The wire body omits "dpa" entirely when include=dpa wasn't requested (verified at unit
        // level in CanonicalAddressMapperTest); a real client-side Jackson deserializer can't tell
        // "absent" from AddressCandidate's own default empty-map field initializer, so both are
        // acceptable here.
        assertThat(response.getBody().getResults().get(0).getDpa()).isNullOrEmpty();

        osPlaces.verify(WireMock.getRequestedFor(WireMock.urlPathEqualTo(OS_PLACES_PATH)));
    }

    @Test
    void nests_the_raw_dpa_record_when_include_dpa_is_requested() {
        final ResponseEntity<AddressSearchResponse> response = postcodeSearch(
                addressesPostcode().queryParam("postcode", "SW1A 2AA").queryParam("include", "dpa"),
                AddressSearchResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getResults().get(0).getDpa())
                .containsEntry("UPRN", "10033544887")
                .containsEntry("BUILDING_NUMBER", "11");
    }

    @Test
    void returns_empty_results_when_os_places_has_no_matches() {
        final ResponseEntity<AddressSearchResponse> response = postcodeSearch(
                addressesPostcode().queryParam("postcode", "ZZ99 1AA"), AddressSearchResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MEDIA_TYPE);
        assertThat(response.getBody().getResults()).isEmpty();
    }

    @Test
    void returns_503_degraded_upstream_rate_limit_when_os_places_returns_429() {
        final ResponseEntity<DegradedResponse> response = postcodeSearch(
                addressesPostcode().queryParam("postcode", "SW1A 3AA"), DegradedResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MEDIA_TYPE);
        assertThat(response.getBody().getDegraded()).isTrue();
        assertThat(response.getBody().getReason().getValue()).isEqualTo("upstream-rate-limit");
        assertThat(response.getBody().getRetryAfterSeconds()).isEqualTo(30);
    }

    @Test
    void returns_503_degraded_upstream_timeout_when_os_places_returns_5xx() {
        final ResponseEntity<DegradedResponse> response = postcodeSearch(
                addressesPostcode().queryParam("postcode", "SW1A 4AA"), DegradedResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MEDIA_TYPE);
        assertThat(response.getBody().getDegraded()).isTrue();
        assertThat(response.getBody().getReason().getValue()).isEqualTo("upstream-timeout");
    }

    @Test
    void returns_503_degraded_upstream_auth_when_os_places_rejects_the_key() {
        final ResponseEntity<DegradedResponse> response = postcodeSearch(
                addressesPostcode().queryParam("postcode", "SW1A 5AA"), DegradedResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().getReason().getValue()).isEqualTo("upstream-auth");
    }

    @Test
    void rejects_missing_postcode_without_calling_os_places() {
        final ResponseEntity<ErrorResponse> response = postcodeSearch(addressesPostcode(), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MEDIA_TYPE);
        assertThat(response.getBody().getError()).isEqualTo("400");

        osPlaces.verify(0, WireMock.getRequestedFor(WireMock.urlPathEqualTo(OS_PLACES_PATH)));
    }

    @Test
    void rejects_unrecognised_query_parameters_without_calling_os_places() {
        final ResponseEntity<ErrorResponse> response = postcodeSearch(
                addressesPostcode().queryParam("postcode", "SW1A 1AA").queryParam("dataset", "LPI"),
                ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MEDIA_TYPE);
        assertThat(response.getBody().getMessage()).isEqualTo("Unrecognised query parameter 'dataset'");

        osPlaces.verify(0, WireMock.getRequestedFor(WireMock.urlPathEqualTo(OS_PLACES_PATH))
                .withQueryParam("dataset", WireMock.equalTo("LPI")));
    }

    @Test
    void never_sends_the_api_key_back_in_any_response_header() {
        final ResponseEntity<AddressSearchResponse> response = postcodeSearch(
                addressesPostcode().queryParam("postcode", "SW1A 1AA"), AddressSearchResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().get("key")).isNull();
    }
}
