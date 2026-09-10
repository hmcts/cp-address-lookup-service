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
        return toResponse(osPlacesClient.searchByPostcode(postcode.trim()), includeDpa);
    }

    @Override
    public AddressSearchResponse searchByAddress(final String address, final boolean includeDpa) {
        return toResponse(osPlacesClient.searchByAddress(address), includeDpa);
    }

    private static AddressSearchResponse toResponse(final List<Map<String, Object>> dpaRecords,
            final boolean includeDpa) {
        final List<AddressCandidate> candidates = dpaRecords.stream()
                .map(dpa -> CanonicalAddressMapper.toCandidate(dpa, includeDpa))
                .toList();
        return new AddressSearchResponse(candidates);
    }
}
