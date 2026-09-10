package uk.gov.hmcts.cp.addresslookup.client;

import java.math.BigDecimal;
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

    /**
     * Searches OS Places by free text. Returns one raw DPA record (field name to value) per
     * candidate, or an empty list when there are no matches.
     *
     * @throws DegradedModeException if OS Places is unavailable, rate-limiting, rejecting the
     *                                configured key, or returns something other than a normal
     *                                response.
     */
    List<Map<String, Object>> searchByAddress(String address);

    /**
     * Matches a free-text address string against OS Places, applying a minimum match-score floor
     * and capping to a single best candidate (OS {@code maxresults=1}). Returns an empty list when
     * {@code address} is nonsense or nothing reaches {@code minMatch} - not an error.
     *
     * @param address  free-text address string to match
     * @param minMatch minimum OS Places match score (0.1-1.0), or {@code null} to use OS's own
     *                 default
     * @throws DegradedModeException if OS Places is unavailable, rate-limiting, rejecting the
     *                                configured key, or returns something other than a normal
     *                                response.
     */
    List<Map<String, Object>> findBestMatch(String address, BigDecimal minMatch);
}
