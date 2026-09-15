package uk.gov.hmcts.cp.addresslookup.config;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Builds operation-tagged, normalised cache keys for the three {@code AddressSearchService}
 * methods, called from each method's {@code @Cacheable(key = ...)} SpEL expression via the
 * {@code T(...)} static-method syntax - declarative annotation-based key config, rather than a
 * {@code KeyGenerator} bean. The operation-name tag ensures the three operations never collide on
 * the same key even when their raw input strings happen to be equal; postcode is normalised
 * (trim/uppercase/whitespace-collapse) for the key only, not for the outbound OS Places call;
 * {@code minMatch} is normalised via {@code stripTrailingZeros()} so {@code 0.7} and {@code 0.70}
 * share one cache entry.
 */
public final class AddressLookupCacheKeys {

    private static final String KEY_DELIMITER = " ";
    private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+");

    private AddressLookupCacheKeys() {
    }

    public static String postcodeKey(final String postcode, final boolean includeDpa) {
        return "postcode" + KEY_DELIMITER + normalisePostcode(postcode) + KEY_DELIMITER + includeDpa;
    }

    public static String findKey(final String address, final boolean includeDpa) {
        return "find" + KEY_DELIMITER + address + KEY_DELIMITER + includeDpa;
    }

    public static String matchKey(final String address, final BigDecimal minMatch) {
        return "match" + KEY_DELIMITER + address + KEY_DELIMITER + normaliseMinMatch(minMatch);
    }

    private static String normalisePostcode(final String postcode) {
        return WHITESPACE_RUN.matcher(postcode.trim()).replaceAll(" ").toUpperCase(Locale.ROOT);
    }

    private static String normaliseMinMatch(final BigDecimal minMatch) {
        return minMatch == null ? "" : minMatch.stripTrailingZeros().toPlainString();
    }
}
