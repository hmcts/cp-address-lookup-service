package uk.gov.hmcts.cp.addresslookup.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;

import lombok.RequiredArgsConstructor;
import uk.gov.hmcts.cp.addresslookup.client.OsPlacesClient;
import uk.gov.hmcts.cp.addresslookup.transform.CanonicalAddressMapper;
import uk.gov.hmcts.cp.openapi.model.al.AddressCandidate;
import uk.gov.hmcts.cp.openapi.model.al.AddressSearchResponse;

/**
 * SB-05: every operation is cache-checked before calling OS Places. Cache keys are
 * operation-tagged so the three operations never collide, even on the same raw input string.
 */
@Service
@RequiredArgsConstructor
public class AddressSearchServiceImpl implements AddressSearchService {

    private static final int MAX_MATCH_RESULTS = 1;
    private static final String KEY_DELIMITER = " ";
    private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+");

    private final OsPlacesClient osPlacesClient;
    private final Cache<String, AddressSearchResponse> addressLookupCache;

    @Override
    public AddressSearchResponse searchByPostcode(final String postcode, final boolean includeDpa) {
        final String key = "postcode" + KEY_DELIMITER + normalisePostcodeForKey(postcode) + KEY_DELIMITER + includeDpa;
        return addressLookupCache.get(key,
                k -> toResponse(osPlacesClient.searchByPostcode(postcode.trim()), includeDpa));
    }

    @Override
    public AddressSearchResponse searchByAddress(final String address, final boolean includeDpa) {
        final String key = "find" + KEY_DELIMITER + address + KEY_DELIMITER + includeDpa;
        return addressLookupCache.get(key, k -> toResponse(osPlacesClient.searchByAddress(address), includeDpa));
    }

    @Override
    public AddressSearchResponse findMatch(final String address, final BigDecimal minMatch) {
        final String key = "match" + KEY_DELIMITER + address + KEY_DELIMITER + normaliseMinMatchForKey(minMatch);
        return addressLookupCache.get(key, k -> {
            final List<AddressCandidate> candidates = osPlacesClient.findBestMatch(address, minMatch).stream()
                    .map(dpa -> CanonicalAddressMapper.toCandidate(dpa, false))
                    .limit(MAX_MATCH_RESULTS)
                    .toList();
            return new AddressSearchResponse(candidates);
        });
    }

    private static AddressSearchResponse toResponse(final List<Map<String, Object>> dpaRecords,
            final boolean includeDpa) {
        final List<AddressCandidate> candidates = dpaRecords.stream()
                .map(dpa -> CanonicalAddressMapper.toCandidate(dpa, includeDpa))
                .toList();
        return new AddressSearchResponse(candidates);
    }

    private static String normalisePostcodeForKey(final String postcode) {
        return WHITESPACE_RUN.matcher(postcode.trim()).replaceAll(" ").toUpperCase(Locale.ROOT);
    }

    private static String normaliseMinMatchForKey(final BigDecimal minMatch) {
        return minMatch == null ? "" : minMatch.stripTrailingZeros().toPlainString();
    }
}
