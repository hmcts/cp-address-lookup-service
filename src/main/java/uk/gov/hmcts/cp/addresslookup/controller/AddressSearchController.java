package uk.gov.hmcts.cp.addresslookup.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.hmcts.cp.addresslookup.service.AddressSearchService;
import uk.gov.hmcts.cp.openapi.api.al.AddressSearchApi;
import uk.gov.hmcts.cp.openapi.model.al.AddressResponseInclude;
import uk.gov.hmcts.cp.openapi.model.al.AddressSearchResponse;

@Slf4j
@RestController
@RequiredArgsConstructor
public class AddressSearchController implements AddressSearchApi {

    private final AddressSearchService addressSearchService;

    @Override
    public ResponseEntity<AddressSearchResponse> searchByPostcode(final String postcode, final AddressResponseInclude include) {
        log.debug("searchByPostcode postcode={} include={}", sanitizeForLog(postcode), include);
        final boolean includeDpa = include == AddressResponseInclude.DPA;
        return ResponseEntity.ok(addressSearchService.searchByPostcode(postcode, includeDpa));
    }

    @Override
    public ResponseEntity<AddressSearchResponse> searchAddresses(final String address, final AddressResponseInclude include) {
        log.debug("searchAddresses address={} include={}", sanitizeForLog(address), include);
        final boolean includeDpa = include == AddressResponseInclude.DPA;
        return ResponseEntity.ok(addressSearchService.searchByAddress(address, includeDpa));
    }

    /**
     * Strips CR/LF from user-supplied input before logging it, so a caller can't inject fake log
     * lines (log forging) via a crafted {@code postcode}/{@code address} query parameter.
     */
    private static String sanitizeForLog(final String value) {
        return value == null ? null : value.replaceAll("[\r\n]", "_");
    }
}
