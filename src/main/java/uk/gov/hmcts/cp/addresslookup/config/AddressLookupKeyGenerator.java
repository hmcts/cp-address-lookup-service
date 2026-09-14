package uk.gov.hmcts.cp.addresslookup.config;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.stereotype.Component;

/**
 * Builds operation-tagged, normalised cache keys for the three {@code AddressSearchService}
 * methods, invoked implicitly by Spring's {@code @Cacheable} proxy. The operation-name tag
 * ensures the three operations never collide on the same key even when their raw input strings
 * happen to be equal; postcode is normalised (trim/uppercase/whitespace-collapse) for the key
 * only, not for the outbound OS Places call; {@code minMatch} is normalised via
 * {@code stripTrailingZeros()} so {@code 0.7} and {@code 0.70} share one cache entry.
 */
@Component("addressLookupKeyGenerator")
public class AddressLookupKeyGenerator implements KeyGenerator {

    private static final String KEY_DELIMITER = " ";
    private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+");

    @Override
    public Object generate(final Object target, final Method method, final Object... params) {
        return switch (method.getName()) {
            case "searchByPostcode" -> "postcode" + KEY_DELIMITER + normalisePostcode((String) params[0])
                    + KEY_DELIMITER + params[1];
            case "searchByAddress" -> "find" + KEY_DELIMITER + params[0] + KEY_DELIMITER + params[1];
            case "findMatch" -> "match" + KEY_DELIMITER + params[0] + KEY_DELIMITER
                    + normaliseMinMatch((BigDecimal) params[1]);
            default -> throw new IllegalStateException("Unexpected @Cacheable method: " + method.getName());
        };
    }

    private static String normalisePostcode(final String postcode) {
        return WHITESPACE_RUN.matcher(postcode.trim()).replaceAll(" ").toUpperCase(Locale.ROOT);
    }

    private static String normaliseMinMatch(final BigDecimal minMatch) {
        return minMatch == null ? "" : minMatch.stripTrailingZeros().toPlainString();
    }
}
