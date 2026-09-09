package uk.gov.hmcts.cp.addresslookup.client;

import java.util.List;
import java.util.Map;

import uk.gov.hmcts.cp.addresslookup.exception.DegradedModeException;

/**
 * Outbound client for the OS Places API.
 */
public interface OsPlacesClient {

    /**
     * Searches OS Places by postcode. Returns one raw DPA record (field name to value) per
     * candidate, or an empty list when the postcode is well-formed but has no matches.
     *
     * @throws DegradedModeException if OS Places is unavailable, rate-limiting, rejecting the
     *                                configured key, or returns something other than a normal
     *                                response.
     */
    List<Map<String, Object>> searchByPostcode(String postcode);
}
