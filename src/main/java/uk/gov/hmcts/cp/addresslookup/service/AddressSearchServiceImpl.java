package uk.gov.hmcts.cp.addresslookup.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.hmcts.cp.addresslookup.client.OsPlacesClient;
import uk.gov.hmcts.cp.addresslookup.config.CacheConfig;
import uk.gov.hmcts.cp.addresslookup.exception.DegradedModeException;
import uk.gov.hmcts.cp.addresslookup.transform.CanonicalAddressMapper;
import uk.gov.hmcts.cp.openapi.model.al.AddressCandidate;
import uk.gov.hmcts.cp.openapi.model.al.AddressSearchResponse;

/**
 * SB-05: every operation is cache-checked before calling OS Places, via {@code @Cacheable}.
 * Composite keys are declared inline via the {@code key} SpEL attribute, calling the static
 * helpers on {@link uk.gov.hmcts.cp.addresslookup.config.AddressLookupCacheKeys} - declarative,
 * annotation-based key config, rather than a separate {@code KeyGenerator} bean. {@code sync =
 * true} preserves stampede protection (concurrent misses for the same key collapse into one OS
 * call); a thrown {@code DegradedModeException} is never cached, since Spring only stores the
 * result after the method returns normally.
 *
 * <p>A single DPA record that {@link CanonicalAddressMapper} can't map (an OS Places record shape
 * we don't recognise) is skipped and logged, not left to fail the whole search - a batch of 100
 * results shouldn't come back empty because one of them is unusual. A batch where every record
 * turns out unmappable still just yields an empty, non-degraded result list, consistent with
 * "zero matches is not degraded" everywhere else in this service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AddressSearchServiceImpl implements AddressSearchService {

    private static final int MAX_MATCH_RESULTS = 1;
    private static final String CACHE_KEYS = "uk.gov.hmcts.cp.addresslookup.config.AddressLookupCacheKeys";

    private final OsPlacesClient osPlacesClient;

    @Override
    @Cacheable(cacheNames = CacheConfig.ADDRESS_LOOKUP_CACHE, sync = true,
            key = "T(" + CACHE_KEYS + ").postcodeKey(#postcode, #includeDpa)")
    public AddressSearchResponse searchByPostcode(final String postcode, final boolean includeDpa) {
        return toResponse(osPlacesClient.searchByPostcode(postcode.trim()), includeDpa);
    }

    @Override
    @Cacheable(cacheNames = CacheConfig.ADDRESS_LOOKUP_CACHE, sync = true,
            key = "T(" + CACHE_KEYS + ").findKey(#address, #includeDpa)")
    public AddressSearchResponse searchByAddress(final String address, final boolean includeDpa) {
        return toResponse(osPlacesClient.searchByAddress(address), includeDpa);
    }

    @Override
    @Cacheable(cacheNames = CacheConfig.ADDRESS_LOOKUP_CACHE, sync = true,
            key = "T(" + CACHE_KEYS + ").matchKey(#address, #minMatch)")
    public AddressSearchResponse findMatch(final String address, final BigDecimal minMatch) {
        final List<AddressCandidate> candidates = osPlacesClient.findBestMatch(address, minMatch).stream()
                .map(dpa -> toCandidateSafely(dpa, false))
                .flatMap(Optional::stream)
                .limit(MAX_MATCH_RESULTS)
                .toList();
        return new AddressSearchResponse(candidates);
    }

    private static AddressSearchResponse toResponse(final List<Map<String, Object>> dpaRecords,
            final boolean includeDpa) {
        final List<AddressCandidate> candidates = dpaRecords.stream()
                .map(dpa -> toCandidateSafely(dpa, includeDpa))
                .flatMap(Optional::stream)
                .toList();
        return new AddressSearchResponse(candidates);
    }

    private static Optional<AddressCandidate> toCandidateSafely(final Map<String, Object> dpa, final boolean includeDpa) {
        AddressCandidate candidate = null;
        try {
            candidate = CanonicalAddressMapper.toCandidate(dpa, includeDpa);
        } catch (final DegradedModeException ex) {
            log.warn("Skipping unmappable OS Places DPA record: {}", ex.getMessage());
        }
        return Optional.ofNullable(candidate);
    }
}
