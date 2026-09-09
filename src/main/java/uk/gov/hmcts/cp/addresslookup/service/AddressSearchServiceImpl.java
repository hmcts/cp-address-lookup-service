package uk.gov.hmcts.cp.addresslookup.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import uk.gov.hmcts.cp.addresslookup.client.OsPlacesClient;
import uk.gov.hmcts.cp.addresslookup.transform.CanonicalAddressMapper;
import uk.gov.hmcts.cp.openapi.model.al.AddressCandidate;
import uk.gov.hmcts.cp.openapi.model.al.AddressSearchResponse;

@Service
@RequiredArgsConstructor
public class AddressSearchServiceImpl implements AddressSearchService {

    private final OsPlacesClient osPlacesClient;

    @Override
    public AddressSearchResponse searchByPostcode(final String postcode, final boolean includeDpa) {
        final List<Map<String, Object>> dpaRecords = osPlacesClient.searchByPostcode(postcode.trim());
        final List<AddressCandidate> candidates = dpaRecords.stream()
                .map(dpa -> CanonicalAddressMapper.toCandidate(dpa, includeDpa))
                .toList();
        return new AddressSearchResponse(candidates);
    }
}
