package uk.gov.hmcts.cp.addresslookup.transform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import uk.gov.hmcts.cp.addresslookup.exception.DegradedModeException;
import uk.gov.hmcts.cp.openapi.model.al.AddressCandidate;
import uk.gov.hmcts.cp.openapi.model.al.DegradedReason;

class CanonicalAddressMapperTest {

    @Test
    void maps_downing_street_example_from_the_contract() {
        final Map<String, Object> dpa = new HashMap<>();
        dpa.put("UPRN", "10033544886");
        dpa.put("BUILDING_NUMBER", "10");
        dpa.put("THOROUGHFARE_NAME", "Downing Street");
        dpa.put("POST_TOWN", "LONDON");
        dpa.put("POSTCODE", "SW1A 1AA");

        final AddressCandidate candidate = CanonicalAddressMapper.toCandidate(dpa, false);

        assertThat(candidate.getAddress1()).isEqualTo("10");
        assertThat(candidate.getAddress2()).isEqualTo("Downing Street");
        assertThat(candidate.getAddress3()).isEqualTo("LONDON");
        assertThat(candidate.getAddress4()).isNull();
        assertThat(candidate.getAddress5()).isNull();
        assertThat(candidate.getPostcode()).isEqualTo("SW1A 1AA");
        assertThat(candidate.getUprn()).isEqualTo("10033544886");
        assertThat(candidate.getDpa()).isNull();
    }

    @Test
    void includes_sub_building_name_when_present() {
        final Map<String, Object> dpa = new HashMap<>();
        dpa.put("UPRN", "10033544886");
        dpa.put("SUB_BUILDING_NAME", "Flat 2");
        dpa.put("BUILDING_NUMBER", "10");
        dpa.put("THOROUGHFARE_NAME", "Downing Street");
        dpa.put("POSTCODE", "SW1A 1AA");

        final AddressCandidate candidate = CanonicalAddressMapper.toCandidate(dpa, false);

        assertThat(candidate.getAddress1()).isEqualTo("Flat 2");
        assertThat(candidate.getAddress2()).isEqualTo("10");
        assertThat(candidate.getAddress3()).isEqualTo("Downing Street");
    }

    @Test
    void nests_raw_dpa_only_when_include_flag_is_set() {
        final Map<String, Object> dpa = new HashMap<>();
        dpa.put("UPRN", "10033544886");
        dpa.put("BUILDING_NUMBER", "10");
        dpa.put("THOROUGHFARE_NAME", "Downing Street");
        dpa.put("POSTCODE", "SW1A 1AA");

        final AddressCandidate withDpa = CanonicalAddressMapper.toCandidate(dpa, true);
        final AddressCandidate withoutDpa = CanonicalAddressMapper.toCandidate(dpa, false);

        assertThat(withDpa.getDpa()).containsEntry("BUILDING_NUMBER", "10");
        assertThat(withoutDpa.getDpa()).isNull();
    }

    @Test
    void truncates_lines_longer_than_35_characters() {
        final Map<String, Object> dpa = new HashMap<>();
        dpa.put("UPRN", "10033544886");
        dpa.put("BUILDING_NAME", "A Very Long Building Name That Exceeds Thirty Five Characters");
        dpa.put("POSTCODE", "SW1A 1AA");

        final AddressCandidate candidate = CanonicalAddressMapper.toCandidate(dpa, false);

        assertThat(candidate.getAddress1()).hasSize(35);
    }

    @Test
    void throws_degraded_upstream_contract_when_uprn_missing() {
        final Map<String, Object> dpa = new HashMap<>();
        dpa.put("BUILDING_NUMBER", "10");
        dpa.put("THOROUGHFARE_NAME", "Downing Street");
        dpa.put("POSTCODE", "SW1A 1AA");

        assertThatThrownBy(() -> CanonicalAddressMapper.toCandidate(dpa, false))
                .isInstanceOf(DegradedModeException.class)
                .extracting(ex -> ((DegradedModeException) ex).getReason())
                .isEqualTo(DegradedReason.UPSTREAM_CONTRACT);
    }

    @Test
    void throws_degraded_upstream_contract_when_no_usable_address_lines() {
        final Map<String, Object> dpa = new HashMap<>();
        dpa.put("UPRN", "10033544886");
        dpa.put("POSTCODE", "SW1A 1AA");

        assertThatThrownBy(() -> CanonicalAddressMapper.toCandidate(dpa, false))
                .isInstanceOf(DegradedModeException.class);
    }
}
