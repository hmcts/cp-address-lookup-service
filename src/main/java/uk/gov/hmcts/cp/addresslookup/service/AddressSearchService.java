package uk.gov.hmcts.cp.addresslookup.service;

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
}
