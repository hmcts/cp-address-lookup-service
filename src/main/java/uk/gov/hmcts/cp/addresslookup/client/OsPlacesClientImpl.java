package uk.gov.hmcts.cp.addresslookup.client;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

import lombok.extern.slf4j.Slf4j;
import uk.gov.hmcts.cp.addresslookup.client.dto.OsPlacesResult;
import uk.gov.hmcts.cp.addresslookup.client.dto.OsPlacesSearchResponse;
import uk.gov.hmcts.cp.addresslookup.config.OsPlacesClientProperties;
import uk.gov.hmcts.cp.addresslookup.exception.DegradedModeException;
import uk.gov.hmcts.cp.openapi.model.al.DegradedReason;

@Slf4j
@Component
public class OsPlacesClientImpl implements OsPlacesClient {

    private static final String POSTCODE_PATH = "/search/places/v1/postcode";
    private static final String FIND_PATH = "/search/places/v1/find";

    private final RestClient restClient;
    private final String apiKey;

    public OsPlacesClientImpl(final RestClient osPlacesRestClient, final OsPlacesClientProperties properties) {
        this.restClient = Objects.requireNonNull(osPlacesRestClient, "osPlacesRestClient");
        this.apiKey = Objects.requireNonNull(properties, "properties").apiKey();
    }

    @Override
    public List<Map<String, Object>> searchByPostcode(final String postcode) {
        return executeSearch(uriBuilder -> uriBuilder.path(POSTCODE_PATH)
                .queryParam("postcode", postcode)
                .queryParam("key", apiKey)
                .build());
    }

    @Override
    public List<Map<String, Object>> searchByAddress(final String address) {
        return executeSearch(uriBuilder -> uriBuilder.path(FIND_PATH)
                .queryParam("query", address)
                .queryParam("key", apiKey)
                .build());
    }

    private List<Map<String, Object>> executeSearch(final Function<UriBuilder, URI> uriCustomizer) {
        final OsPlacesSearchResponse response;
        try {
            response = restClient.get()
                    .uri(uriCustomizer)
                    .retrieve()
                    .body(OsPlacesSearchResponse.class);
        } catch (final HttpClientErrorException.TooManyRequests ex) {
            throw degraded(DegradedReason.UPSTREAM_RATE_LIMIT, retryAfterSeconds(ex), "OS Places rate limit exceeded", ex);
        } catch (final HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden ex) {
            throw degraded(DegradedReason.UPSTREAM_AUTH, null, "OS Places rejected the configured API key", ex);
        } catch (final HttpServerErrorException ex) {
            throw degraded(DegradedReason.UPSTREAM_TIMEOUT, null, "OS Places returned a server error", ex);
        } catch (final HttpClientErrorException ex) {
            throw degraded(DegradedReason.UPSTREAM_CONTRACT, null, "OS Places rejected the request unexpectedly", ex);
        } catch (final ResourceAccessException ex) {
            throw degraded(DegradedReason.UPSTREAM_TIMEOUT, null, "OS Places did not respond in time", ex);
        } catch (final RestClientException ex) {
            throw degraded(DegradedReason.UPSTREAM_CONTRACT, null, "OS Places response could not be read", ex);
        }

        final List<Map<String, Object>> results;
        if (response == null || response.results() == null) {
            results = List.of();
        } else {
            results = response.results().stream()
                    .map(OsPlacesResult::dpa)
                    .filter(Objects::nonNull)
                    .toList();
        }
        return results;
    }

    private static Integer retryAfterSeconds(final HttpClientErrorException ex) {
        Integer seconds = null;
        final HttpHeaders headers = ex.getResponseHeaders();
        final String retryAfter = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (retryAfter != null) {
            try {
                seconds = Integer.valueOf(retryAfter.trim());
            } catch (final NumberFormatException ignored) {
                // seconds stays null (already initialised above)
            }
        }
        return seconds;
    }

    private static DegradedModeException degraded(final DegradedReason reason, final Integer retryAfterSeconds,
            final String message, final Exception cause) {
        log.warn("OS Places call degraded: reason={} message={}", reason, message, cause);
        return new DegradedModeException(reason, retryAfterSeconds, message, cause);
    }
}
