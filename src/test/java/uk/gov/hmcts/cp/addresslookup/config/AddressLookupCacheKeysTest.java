package uk.gov.hmcts.cp.addresslookup.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class AddressLookupCacheKeysTest {

    @Test
    void postcode_key_is_case_and_whitespace_insensitive() {
        assertThat(AddressLookupCacheKeys.postcodeKey("SW1A 1AA", false))
                .isEqualTo(AddressLookupCacheKeys.postcodeKey("sw1a  1aa", false));
    }

    @Test
    void postcode_key_differs_by_include_dpa() {
        assertThat(AddressLookupCacheKeys.postcodeKey("SW1A 1AA", false))
                .isNotEqualTo(AddressLookupCacheKeys.postcodeKey("SW1A 1AA", true));
    }

    @Test
    void find_key_is_case_sensitive_and_exact() {
        assertThat(AddressLookupCacheKeys.findKey("10 Downing Street", false))
                .isNotEqualTo(AddressLookupCacheKeys.findKey("10 downing street", false));
    }

    @Test
    void match_key_treats_min_match_scale_variants_as_equal() {
        assertThat(AddressLookupCacheKeys.matchKey("10 Downing Street", new BigDecimal("0.7")))
                .isEqualTo(AddressLookupCacheKeys.matchKey("10 Downing Street", new BigDecimal("0.70")));
    }

    @Test
    void match_key_treats_null_min_match_consistently() {
        assertThat(AddressLookupCacheKeys.matchKey("10 Downing Street", null))
                .isEqualTo(AddressLookupCacheKeys.matchKey("10 Downing Street", null));
    }

    @Test
    void operation_tag_prevents_collisions_across_operations_on_the_same_raw_value() {
        assertThat(AddressLookupCacheKeys.postcodeKey("shared value", false))
                .isNotEqualTo(AddressLookupCacheKeys.findKey("shared value", false));
        assertThat(AddressLookupCacheKeys.findKey("shared value", false))
                .isNotEqualTo(AddressLookupCacheKeys.matchKey("shared value", null));
    }
}
