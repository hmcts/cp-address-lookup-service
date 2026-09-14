package uk.gov.hmcts.cp.addresslookup.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import uk.gov.hmcts.cp.addresslookup.client.OsPlacesClient;
import uk.gov.hmcts.cp.addresslookup.config.CacheConfig;
import uk.gov.hmcts.cp.addresslookup.transform.CanonicalAddressMapper;
import uk.gov.hmcts.cp.openapi.model.al.AddressCandidate;
import uk.gov.hmcts.cp.openapi.model.al.AddressSearchResponse;

/**
 * SB-05: every operation is cache-checked before calling OS Places, via {@code @Cacheable}
 * (key logic in {@link uk.gov.hmcts.cp.addresslookup.config.AddressLookupKeyGenerator}).
 * {@code sync = true} preserves stampede protection (concurrent misses for the same key collapse
 * into one OS call); a thrown {@code DegradedModeException} is never cached, since Spring only
 * stores the result after the method returns normally.
 */
@Service
@RequiredArgsConstructor
public class AddressSearchServiceImpl implements AddressSearchService {

    private static final int MAX_MATCH_RESULTS = 1;
    private static final String KEY_GENERATOR = "addressLookupKeyGenerator";

    private final OsPlacesClient osPlacesClient;

    @Override
    @Cacheable(cacheNames = CacheConfig.ADDRESS_LOOKUP_CACHE, keyGenerator = KEY_GENERATOR, sync = true)
    public AddressSearchResponse searchByPostcode(final String postcode, final boolean includeDpa) {
        return toResponse(osPlacesClient.searchByPostcode(postcode.trim()), includeDpa);
    }

    @Override
    @Cacheable(cacheNames = CacheConfig.ADDRESS_LOOKUP_CACHE, keyGenerator = KEY_GENERATOR, sync = true)
    public AddressSearchResponse searchByAddress(final String address, final boolean includeDpa) {
        return toResponse(osPlacesClient.searchByAddress(address), includeDpa);
    }

    @Override
    @Cacheable(cacheNames = CacheConfig.ADDRESS_LOOKUP_CACHE, keyGenerator = KEY_GENERATOR, sync = true)
    public AddressSearchResponse findMatch(final String address, final BigDecimal minMatch) {
        final List<AddressCandidate> candidates = osPlacesClient.findBestMatch(address, minMatch).stream()
                .map(dpa -> CanonicalAddressMapper.toCandidate(dpa, false))
                .limit(MAX_MATCH_RESULTS)
                .toList();
        return new AddressSearchResponse(candidates);
    }

    private static AddressSearchResponse toResponse(final List<Map<String, Object>> dpaRecords,
            final boolean includeDpa) {
        final List<AddressCandidate> candidates = dpaRecords.stream()
                .map(dpa -> CanonicalAddressMapper.toCandidate(dpa, includeDpa))
                .toList();
        return new AddressSearchResponse(candidates);
    }
}
