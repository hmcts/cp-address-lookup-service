package uk.gov.hmcts.cp.addresslookup.service;

import java.math.BigDecimal;

import uk.gov.hmcts.cp.openapi.model.al.AddressSearchResponse;

public interface AddressSearchService {

    /**
     * Searches for addresses matching the given postcode.
     *
     * @param postcode   full or partial UK postcode
     * @param includeDpa whether to nest the raw OS Places DPA record on each candidate
     */
    AddressSearchResponse searchByPostcode(String postcode, boolean includeDpa);

    /**
     * Searches for addresses matching the given free text. Forwarded to OS Places as-is - no
     * server-side normalisation, unlike {@link #searchByPostcode}.
     *
     * @param address    free-text address, optionally including a postcode
     * @param includeDpa whether to nest the raw OS Places DPA record on each candidate
     */
    AddressSearchResponse searchByAddress(String address, boolean includeDpa);

    /**
     * Matches a free-text address against OS Places, returning at most one candidate carrying a
     * match score. {@code include=dpa} is not part of this operation's contract, so raw DPA is
     * never nested here.
     *
     * @param address  free-text address string to match
     * @param minMatch minimum OS Places match score (0.1-1.0), or {@code null} for OS's own default
     */
    AddressSearchResponse findMatch(String address, BigDecimal minMatch);
}
