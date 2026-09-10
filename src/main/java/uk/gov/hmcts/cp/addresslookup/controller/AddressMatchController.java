package uk.gov.hmcts.cp.addresslookup.controller;

import java.math.BigDecimal;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.hmcts.cp.addresslookup.service.AddressSearchService;
import uk.gov.hmcts.cp.openapi.api.al.AddressMatchApi;
import uk.gov.hmcts.cp.openapi.model.al.AddressSearchResponse;

@Slf4j
@RestController
@RequiredArgsConstructor
public class AddressMatchController implements AddressMatchApi {

    private final AddressSearchService addressSearchService;

    @Override
    public ResponseEntity<AddressSearchResponse> findAddress(final String address, final BigDecimal minMatch) {
        log.debug("findAddress address={} minMatch={}", address, minMatch);
        return ResponseEntity.ok(addressSearchService.findMatch(address, minMatch));
    }
}
