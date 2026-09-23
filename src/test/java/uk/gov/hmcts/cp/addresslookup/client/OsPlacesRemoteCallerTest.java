package uk.gov.hmcts.cp.addresslookup.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import uk.gov.hmcts.cp.addresslookup.client.dto.OsPlacesSearchResponse;

/**
 * Proves the actual outbound HTTP call shape (URL, query params) and how OS Places' raw HTTP
 * responses surface once joined - SB-06 split this out of what used to be
 * {@code OsPlacesClientImplTest} (which now only covers the failure-to-{@code DegradedReason}
 * mapping, against a mocked {@link OsPlacesRemoteCaller}) since that's the class the
 * {@code @CircuitBreaker}/{@code @TimeLimiter} annotations - and therefore the real {@link RestClient}
 * call - now live on.
 */
class OsPlacesRemoteCallerTest {

    private static final String BASE_URL = "https://os-places.test";
    private static final String API_KEY = "test-key";

    private final RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    // The @CircuitBreaker/@TimeLimiter annotations are inert on a plain `new` instance anyway
    // (they only apply via Spring AOP proxying), so nothing here depends on which executor runs
    // the supplier - a real one is just as fine as a same-thread one would be.
    private final OsPlacesRemoteCaller remoteCaller =
            new OsPlacesRemoteCaller(builder.build(), Executors.newVirtualThreadPerTaskExecutor());

    @Test
    void postcode_returns_the_parsed_response_on_success() {
        server.expect(requestTo(BASE_URL + "/search/places/v1/postcode?postcode=SW1A%201AA&key=test-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"results":[{"DPA":{"UPRN":"10033544886","BUILDING_NUMBER":"10","THOROUGHFARE_NAME":"Downing Street","POSTCODE":"SW1A 1AA"}}]}
                        """, MediaType.APPLICATION_JSON));

        final OsPlacesSearchResponse response = remoteCaller.postcode("SW1A 1AA", API_KEY).join();

        assertThat(response.results()).hasSize(1);
        assertThat(response.results().get(0).dpa()).containsEntry("UPRN", "10033544886");
        server.verify();
    }

    @Test
    void postcode_appends_the_api_key_as_a_query_param() {
        server.expect(requestTo(BASE_URL + "/search/places/v1/postcode?postcode=SW1A%201AA&key=test-key"))
                .andExpect(queryParam("key", "test-key"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        remoteCaller.postcode("SW1A 1AA", API_KEY).join();

        server.verify();
    }

    @Test
    void postcode_429_surfaces_as_too_many_requests_with_retry_after() {
        server.expect(requestTo(BASE_URL + "/search/places/v1/postcode?postcode=SW1A%201AA&key=test-key"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .header(HttpHeaders.RETRY_AFTER, "30")
                        .body("{}"));

        assertThatThrownBy(() -> remoteCaller.postcode("SW1A 1AA", API_KEY).join())
                .isInstanceOf(CompletionException.class)
                .cause().isInstanceOf(HttpClientErrorException.TooManyRequests.class)
                .satisfies(ex -> assertThat(((HttpClientErrorException) ex).getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER))
                        .isEqualTo("30"));
    }

    @Test
    void postcode_401_surfaces_as_unauthorized() {
        server.expect(requestTo(BASE_URL + "/search/places/v1/postcode?postcode=SW1A%201AA&key=test-key"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{}"));

        assertThatThrownBy(() -> remoteCaller.postcode("SW1A 1AA", API_KEY).join())
                .isInstanceOf(CompletionException.class)
                .cause().isInstanceOf(HttpClientErrorException.Unauthorized.class);
    }

    @Test
    void postcode_500_surfaces_as_internal_server_error() {
        server.expect(requestTo(BASE_URL + "/search/places/v1/postcode?postcode=SW1A%201AA&key=test-key"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> remoteCaller.postcode("SW1A 1AA", API_KEY).join())
                .isInstanceOf(CompletionException.class)
                .cause().isInstanceOf(HttpServerErrorException.InternalServerError.class);
    }

    @Test
    void postcode_malformed_json_surfaces_as_a_rest_client_exception() {
        server.expect(requestTo(BASE_URL + "/search/places/v1/postcode?postcode=SW1A%201AA&key=test-key"))
                .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> remoteCaller.postcode("SW1A 1AA", API_KEY).join())
                .isInstanceOf(CompletionException.class)
                .cause().isInstanceOf(RestClientException.class);
    }

    @Test
    void postcode_connection_failure_surfaces_as_resource_access_exception() {
        final RestClient failingClient = RestClient.builder().baseUrl("http://127.0.0.1:1").build();
        final OsPlacesRemoteCaller callerWithBadHost =
                new OsPlacesRemoteCaller(failingClient, Executors.newVirtualThreadPerTaskExecutor());

        assertThatThrownBy(() -> callerWithBadHost.postcode("SW1A 1AA", API_KEY).join())
                .isInstanceOf(CompletionException.class)
                .cause().isInstanceOf(ResourceAccessException.class);
    }

    @Test
    void find_uses_query_param_not_postcode() {
        server.expect(requestTo(BASE_URL + "/search/places/v1/find?query=10%20Downing%20Street&key=test-key"))
                .andExpect(queryParam("query", "10%20Downing%20Street"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        remoteCaller.find("10 Downing Street", API_KEY).join();

        server.verify();
    }

    @Test
    void find_returns_the_parsed_response_on_success() {
        server.expect(requestTo(BASE_URL + "/search/places/v1/find?query=10%20Downing%20Street&key=test-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"results":[{"DPA":{"UPRN":"10033544886","BUILDING_NUMBER":"10","THOROUGHFARE_NAME":"Downing Street","POSTCODE":"SW1A 1AA"}}]}
                        """, MediaType.APPLICATION_JSON));

        final OsPlacesSearchResponse response = remoteCaller.find("10 Downing Street", API_KEY).join();

        assertThat(response.results()).hasSize(1);
        server.verify();
    }

    @Test
    void match_sends_minmatch_and_maxresults_of_one() {
        server.expect(requestTo(BASE_URL
                        + "/search/places/v1/find?query=10%20Downing%20Street&maxresults=1&minmatch=0.7&key=test-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"results":[{"DPA":{"UPRN":"10033544886","BUILDING_NUMBER":"10","THOROUGHFARE_NAME":"Downing Street","POSTCODE":"SW1A 1AA","MATCH":"0.95"}}]}
                        """, MediaType.APPLICATION_JSON));

        final OsPlacesSearchResponse response = remoteCaller.match("10 Downing Street", new BigDecimal("0.7"), API_KEY).join();

        assertThat(response.results()).hasSize(1);
        assertThat(response.results().get(0).dpa()).containsEntry("MATCH", "0.95");
        server.verify();
    }

    @Test
    void match_omits_minmatch_when_not_provided() {
        server.expect(requestTo(BASE_URL + "/search/places/v1/find?query=10%20Downing%20Street&maxresults=1&key=test-key"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        remoteCaller.match("10 Downing Street", null, API_KEY).join();

        server.verify();
    }
}
