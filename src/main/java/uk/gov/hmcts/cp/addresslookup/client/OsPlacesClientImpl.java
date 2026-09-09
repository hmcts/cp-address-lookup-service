package uk.gov.hmcts.cp.addresslookup.client;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import lombok.extern.slf4j.Slf4j;
import uk.gov.hmcts.cp.addresslookup.client.dto.OsPlacesPostcodeResponse;
import uk.gov.hmcts.cp.addresslookup.client.dto.OsPlacesResult;
import uk.gov.hmcts.cp.addresslookup.config.OsPlacesClientProperties;
import uk.gov.hmcts.cp.addresslookup.exception.DegradedModeException;
import uk.gov.hmcts.cp.openapi.model.al.DegradedReason;

@Slf4j
@Component
public class OsPlacesClientImpl implements OsPlacesClient {

    private static final String POSTCODE_PATH = "/search/places/v1/postcode";

    private final RestClient restClient;
    private final String apiKey;

    public OsPlacesClientImpl(final RestClient osPlacesRestClient, final OsPlacesClientProperties properties) {
        this.restClient = Objects.requireNonNull(osPlacesRestClient, "osPlacesRestClient");
        this.apiKey = Objects.requireNonNull(properties, "properties").apiKey();
    }

    @Override
    public List<Map<String, Object>> searchByPostcode(final String postcode) {
        final OsPlacesPostcodeResponse response;
        try {
            response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path(POSTCODE_PATH)
                            .queryParam("postcode", postcode)
                            .queryParam("key", apiKey)
                            .build())
                    .retrieve()
                    .body(OsPlacesPostcodeResponse.class);
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

        if (response == null || response.results() == null) {
            return List.of();
        }
        return response.results().stream()
                .map(OsPlacesResult::dpa)
                .filter(Objects::nonNull)
                .toList();
    }

    private static Integer retryAfterSeconds(final HttpClientErrorException ex) {
        final HttpHeaders headers = ex.getResponseHeaders();
        if (headers == null) {
            return null;
        }
        final String retryAfter = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (retryAfter == null) {
            return null;
        }
        try {
            return Integer.valueOf(retryAfter.trim());
        } catch (final NumberFormatException ignored) {
            return null;
        }
    }

    private static DegradedModeException degraded(final DegradedReason reason, final Integer retryAfterSeconds,
            final String message, final Exception cause) {
        log.warn("OS Places call degraded: reason={} message={}", reason, message, cause);
        return new DegradedModeException(reason, retryAfterSeconds, message, cause);
    }
}
