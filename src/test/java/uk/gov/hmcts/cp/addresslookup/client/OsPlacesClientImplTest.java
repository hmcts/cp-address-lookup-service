package uk.gov.hmcts.cp.addresslookup.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import uk.gov.hmcts.cp.addresslookup.config.OsPlacesClientProperties;
import uk.gov.hmcts.cp.addresslookup.exception.DegradedModeException;
import uk.gov.hmcts.cp.openapi.model.al.DegradedReason;

class OsPlacesClientImplTest {

    private static final String BASE_URL = "https://os-places.test";

    private final RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final OsPlacesClientProperties properties =
            new OsPlacesClientProperties(BASE_URL, "test-key", 3000, 10_000, false);
    private final OsPlacesClientImpl client = new OsPlacesClientImpl(builder.build(), properties);

    @Test
    void returns_dpa_records_on_success() {
        server.expect(requestTo(BASE_URL + "/search/places/v1/postcode?postcode=SW1A%201AA&key=test-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"results":[{"DPA":{"UPRN":"10033544886","BUILDING_NUMBER":"10","THOROUGHFARE_NAME":"Downing Street","POSTCODE":"SW1A 1AA"}}]}
                        """, MediaType.APPLICATION_JSON));

        final List<Map<String, Object>> results = client.searchByPostcode("SW1A 1AA");

        assertThat(results).hasSize(1);
        assertThat(results.get(0)).containsEntry("UPRN", "10033544886");
        server.verify();
    }

    @Test
    void returns_empty_list_when_results_is_null() {
        server.expect(requestTo(BASE_URL + "/search/places/v1/postcode?postcode=ZZ99%201AA&key=test-key"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(client.searchByPostcode("ZZ99 1AA")).isEmpty();
    }

    @Test
    void appends_the_api_key_as_a_query_param() {
        server.expect(requestTo(BASE_URL + "/search/places/v1/postcode?postcode=SW1A%201AA&key=test-key"))
                .andExpect(queryParam("key", "test-key"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.searchByPostcode("SW1A 1AA");

        server.verify();
    }

    @Test
    void maps_429_to_upstream_rate_limit_with_retry_after() {
        server.expect(requestTo(BASE_URL + "/search/places/v1/postcode?postcode=SW1A%201AA&key=test-key"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .header(HttpHeaders.RETRY_AFTER, "30")
                        .body("{}"));

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
        server.expect(requestTo(BASE_URL + "/search/places/v1/postcode?postcode=SW1A%201AA&key=test-key"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{}"));

        assertThatThrownBy(() -> client.searchByPostcode("SW1A 1AA"))
                .isInstanceOf(DegradedModeException.class)
                .extracting(ex -> ((DegradedModeException) ex).getReason())
                .isEqualTo(DegradedReason.UPSTREAM_AUTH);
    }

    @Test
    void maps_5xx_to_upstream_timeout() {
        server.expect(requestTo(BASE_URL + "/search/places/v1/postcode?postcode=SW1A%201AA&key=test-key"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.searchByPostcode("SW1A 1AA"))
                .isInstanceOf(DegradedModeException.class)
                .extracting(ex -> ((DegradedModeException) ex).getReason())
                .isEqualTo(DegradedReason.UPSTREAM_TIMEOUT);
    }

    @Test
    void maps_malformed_json_to_upstream_contract() {
        server.expect(requestTo(BASE_URL + "/search/places/v1/postcode?postcode=SW1A%201AA&key=test-key"))
                .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.searchByPostcode("SW1A 1AA"))
                .isInstanceOf(DegradedModeException.class)
                .extracting(ex -> ((DegradedModeException) ex).getReason())
                .isEqualTo(DegradedReason.UPSTREAM_CONTRACT);
    }

    @Test
    void wraps_connection_failures_as_upstream_timeout() {
        final RestClient failingClient = RestClient.builder().baseUrl("http://127.0.0.1:1").build();
        final OsPlacesClientImpl clientWithBadHost = new OsPlacesClientImpl(failingClient, properties);

        assertThatThrownBy(() -> clientWithBadHost.searchByPostcode("SW1A 1AA"))
                .isInstanceOf(DegradedModeException.class)
                .extracting(ex -> ((DegradedModeException) ex).getReason())
                .isEqualTo(DegradedReason.UPSTREAM_TIMEOUT);
    }

    @Test
    void address_search_returns_dpa_records_on_success() {
        server.expect(requestTo(BASE_URL + "/search/places/v1/find?query=10%20Downing%20Street&key=test-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"results":[{"DPA":{"UPRN":"10033544886","BUILDING_NUMBER":"10","THOROUGHFARE_NAME":"Downing Street","POSTCODE":"SW1A 1AA"}}]}
                        """, MediaType.APPLICATION_JSON));

        final List<Map<String, Object>> results = client.searchByAddress("10 Downing Street");

        assertThat(results).hasSize(1);
        assertThat(results.get(0)).containsEntry("UPRN", "10033544886");
        server.verify();
    }

    @Test
    void address_search_returns_empty_list_when_results_is_null() {
        server.expect(requestTo(BASE_URL + "/search/places/v1/find?query=nonsense&key=test-key"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(client.searchByAddress("nonsense")).isEmpty();
    }

    @Test
    void address_search_uses_query_param_not_postcode() {
        server.expect(requestTo(BASE_URL + "/search/places/v1/find?query=10%20Downing%20Street&key=test-key"))
                .andExpect(queryParam("query", "10%20Downing%20Street"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.searchByAddress("10 Downing Street");

        server.verify();
    }

    @Test
    void address_search_maps_429_to_upstream_rate_limit_with_retry_after() {
        server.expect(requestTo(BASE_URL + "/search/places/v1/find?query=10%20Downing%20Street&key=test-key"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .header(HttpHeaders.RETRY_AFTER, "30")
                        .body("{}"));

        assertThatThrownBy(() -> client.searchByAddress("10 Downing Street"))
                .isInstanceOf(DegradedModeException.class)
                .satisfies(ex -> {
                    final DegradedModeException degraded = (DegradedModeException) ex;
                    assertThat(degraded.getReason()).isEqualTo(DegradedReason.UPSTREAM_RATE_LIMIT);
                    assertThat(degraded.getRetryAfterSeconds()).isEqualTo(30);
                });
    }

    @Test
    void address_search_maps_401_to_upstream_auth() {
        server.expect(requestTo(BASE_URL + "/search/places/v1/find?query=10%20Downing%20Street&key=test-key"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{}"));

        assertThatThrownBy(() -> client.searchByAddress("10 Downing Street"))
                .isInstanceOf(DegradedModeException.class)
                .extracting(ex -> ((DegradedModeException) ex).getReason())
                .isEqualTo(DegradedReason.UPSTREAM_AUTH);
    }

    @Test
    void address_search_maps_5xx_to_upstream_timeout() {
        server.expect(requestTo(BASE_URL + "/search/places/v1/find?query=10%20Downing%20Street&key=test-key"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.searchByAddress("10 Downing Street"))
                .isInstanceOf(DegradedModeException.class)
                .extracting(ex -> ((DegradedModeException) ex).getReason())
                .isEqualTo(DegradedReason.UPSTREAM_TIMEOUT);
    }

    @Test
    void address_search_maps_malformed_json_to_upstream_contract() {
        server.expect(requestTo(BASE_URL + "/search/places/v1/find?query=10%20Downing%20Street&key=test-key"))
                .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.searchByAddress("10 Downing Street"))
                .isInstanceOf(DegradedModeException.class)
                .extracting(ex -> ((DegradedModeException) ex).getReason())
                .isEqualTo(DegradedReason.UPSTREAM_CONTRACT);
    }

    @Test
    void address_search_wraps_connection_failures_as_upstream_timeout() {
        final RestClient failingClient = RestClient.builder().baseUrl("http://127.0.0.1:1").build();
        final OsPlacesClientImpl clientWithBadHost = new OsPlacesClientImpl(failingClient, properties);

        assertThatThrownBy(() -> clientWithBadHost.searchByAddress("10 Downing Street"))
                .isInstanceOf(DegradedModeException.class)
                .extracting(ex -> ((DegradedModeException) ex).getReason())
                .isEqualTo(DegradedReason.UPSTREAM_TIMEOUT);
    }
}
