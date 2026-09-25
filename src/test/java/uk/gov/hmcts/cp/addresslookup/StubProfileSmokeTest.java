package uk.gov.hmcts.cp.addresslookup;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.annotation.Resource;
import uk.gov.hmcts.cp.openapi.model.al.AddressSearchResponse;
import uk.gov.hmcts.cp.openapi.model.al.DegradedResponse;

/**
 * SB-07: proves the shipped {@code stub} profile itself works end-to-end - a real HTTP round trip
 * through the packaged app, {@code stub} profile active with the {@code ste} fixture set
 * selected, hitting the in-process WireMock server {@code StubOsPlacesConfig} starts. Distinct
 * from the existing WireMock integration tests, which exercise business logic against an ad-hoc
 * test double rather than the actual profile/module that ships to STE/DEV/NFT.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {"spring.profiles.active=stub", "os-places.stub.fixture-set=ste"})
@AutoConfigureTestRestTemplate
// The embedded WireMock server this context starts binds a fixed port (9561), so its context must
// not be cached/reused past this class - otherwise it can collide with any other test in the same
// JVM run that also needs that port (e.g. StubOsPlacesConfigTest's own direct bean invocation).
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class StubProfileSmokeTest {

    @Resource
    private TestRestTemplate restTemplate;

    @Test
    void postcode_search_is_served_from_the_stub_fixture_corpus() {
        final ResponseEntity<AddressSearchResponse> response = get(
                UriComponentsBuilder.fromPath("/addresses/postcode").queryParam("postcode", "SW1A 1AA"),
                "application/vnd.addresslookup-service.addresses-postcode+json", AddressSearchResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getResults()).hasSize(1);
        assertThat(response.getBody().getResults().get(0).getLine1()).isEqualTo("10 Downing Street");
    }

    @Test
    void free_text_search_is_served_from_the_stub_fixture_corpus() {
        final ResponseEntity<AddressSearchResponse> response = get(
                UriComponentsBuilder.fromPath("/addresses").queryParam("address", "10 Downing Street"),
                "application/vnd.addresslookup-service.addresses+json", AddressSearchResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getResults()).hasSize(1);
    }

    @Test
    void match_is_served_from_the_stub_fixture_corpus() {
        final ResponseEntity<AddressSearchResponse> response = get(
                UriComponentsBuilder.fromPath("/addresses/find").queryParam("address", "10 Downing Street"),
                "application/vnd.addresslookup-service.addresses-find+json", AddressSearchResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getResults()).hasSize(1);
        assertThat(response.getBody().getResults().get(0).getMatch()).isNotNull();
    }

    @Test
    void degraded_fixtures_are_reachable_through_the_stub_too() {
        final ResponseEntity<DegradedResponse> response = get(
                UriComponentsBuilder.fromPath("/addresses/postcode").queryParam("postcode", "SW1A 3AA"),
                "application/vnd.addresslookup-service.addresses-postcode+json", DegradedResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().getReason().getValue()).isEqualTo("upstream-rate-limit");
    }

    private <T> ResponseEntity<T> get(final UriComponentsBuilder query, final String acceptMediaType,
            final Class<T> responseType) {
        final URI uri = query.build().encode().toUri();
        final HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.parseMediaType(acceptMediaType)));
        return restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), responseType);
    }
}
