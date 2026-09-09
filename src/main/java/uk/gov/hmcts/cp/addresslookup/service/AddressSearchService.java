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
}
