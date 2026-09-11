package uk.gov.hmcts.cp.addresslookup.transform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import uk.gov.hmcts.cp.addresslookup.exception.DegradedModeException;
import uk.gov.hmcts.cp.openapi.model.al.AddressCandidate;
import uk.gov.hmcts.cp.openapi.model.al.DegradedReason;

class CanonicalAddressMapperTest {

    @Test
    void maps_downing_street_combining_building_number_and_street_on_one_line() {
        // Ported from cpp-ui-pdk2's osDpaToAddress: BUILDING_NUMBER + THOROUGHFARE_NAME combine
        // onto one line, unlike the OpenAPI spec's own (different) worked example.
        final Map<String, Object> dpa = new HashMap<>();
        dpa.put("UPRN", "10033544886");
        dpa.put("BUILDING_NUMBER", "10");
        dpa.put("THOROUGHFARE_NAME", "Downing Street");
        dpa.put("POST_TOWN", "LONDON");
        dpa.put("POSTCODE", "SW1A 1AA");

        final AddressCandidate candidate = CanonicalAddressMapper.toCandidate(dpa, false);

        assertThat(candidate.getAddress1()).isEqualTo("10 Downing Street");
        assertThat(candidate.getAddress2()).isNull();
        assertThat(candidate.getAddress3()).isNull();
        assertThat(candidate.getAddress4()).isEqualTo("LONDON");
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
        assertThat(candidate.getAddress2()).isEqualTo("10 Downing Street");
        assertThat(candidate.getAddress3()).isNull();
    }

    @Test
    void building_name_is_its_own_line_separate_from_building_number_and_street() {
        final Map<String, Object> dpa = new HashMap<>();
        dpa.put("UPRN", "10033544886");
        dpa.put("BUILDING_NAME", "Downing House");
        dpa.put("BUILDING_NUMBER", "10");
        dpa.put("THOROUGHFARE_NAME", "Downing Street");
        dpa.put("POSTCODE", "SW1A 1AA");

        final AddressCandidate candidate = CanonicalAddressMapper.toCandidate(dpa, false);

        assertThat(candidate.getAddress1()).isEqualTo("Downing House");
        assertThat(candidate.getAddress2()).isEqualTo("10 Downing Street");
        assertThat(candidate.getAddress3()).isNull();
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
    void uses_organisation_name_as_address1_for_a_named_premise_with_no_street_fields() {
        // Real OS Places record for Buckingham Palace: no BUILDING_NUMBER/NAME or
        // THOROUGHFARE_NAME at all, only ORGANISATION_NAME, POST_TOWN and
        // LOCAL_CUSTODIAN_CODE_DESCRIPTION.
        final Map<String, Object> dpa = new HashMap<>();
        dpa.put("UPRN", "10033544614");
        dpa.put("ORGANISATION_NAME", "BUCKINGHAM PALACE");
        dpa.put("POST_TOWN", "LONDON");
        dpa.put("LOCAL_CUSTODIAN_CODE_DESCRIPTION", "CITY OF WESTMINSTER");
        dpa.put("POSTCODE", "SW1A 1AA");

        final AddressCandidate candidate = CanonicalAddressMapper.toCandidate(dpa, false);

        assertThat(candidate.getAddress1()).isEqualTo("BUCKINGHAM PALACE");
        assertThat(candidate.getAddress2()).isNull();
        assertThat(candidate.getAddress3()).isNull();
        assertThat(candidate.getAddress4()).isEqualTo("LONDON");
        assertThat(candidate.getAddress5()).isEqualTo("CITY OF WESTMINSTER");
    }

    @Test
    void organisation_name_leads_even_when_street_fields_are_also_present() {
        final Map<String, Object> dpa = new HashMap<>();
        dpa.put("UPRN", "10033544886");
        dpa.put("ORGANISATION_NAME", "HMCTS");
        dpa.put("BUILDING_NUMBER", "10");
        dpa.put("THOROUGHFARE_NAME", "Downing Street");
        dpa.put("POSTCODE", "SW1A 1AA");

        final AddressCandidate candidate = CanonicalAddressMapper.toCandidate(dpa, false);

        assertThat(candidate.getAddress1()).isEqualTo("HMCTS");
        assertThat(candidate.getAddress2()).isEqualTo("10 Downing Street");
        assertThat(candidate.getAddress3()).isNull();
    }

    @Test
    void address4_and_address5_are_absent_when_post_town_and_local_custodian_code_description_are_absent() {
        final Map<String, Object> dpa = new HashMap<>();
        dpa.put("UPRN", "10033544886");
        dpa.put("BUILDING_NUMBER", "10");
        dpa.put("THOROUGHFARE_NAME", "Downing Street");
        dpa.put("POSTCODE", "SW1A 1AA");

        final AddressCandidate candidate = CanonicalAddressMapper.toCandidate(dpa, false);

        assertThat(candidate.getAddress4()).isNull();
        assertThat(candidate.getAddress5()).isNull();
    }

    @Test
    void address4_and_address5_are_truncated_to_35_characters() {
        final Map<String, Object> dpa = new HashMap<>();
        dpa.put("UPRN", "10033544886");
        dpa.put("BUILDING_NUMBER", "10");
        dpa.put("THOROUGHFARE_NAME", "Downing Street");
        dpa.put("POST_TOWN", "A Very Long Post Town Name That Exceeds Thirty Five Characters");
        dpa.put("LOCAL_CUSTODIAN_CODE_DESCRIPTION", "A Very Long Custodian Description That Exceeds Thirty Five Characters");
        dpa.put("POSTCODE", "SW1A 1AA");

        final AddressCandidate candidate = CanonicalAddressMapper.toCandidate(dpa, false);

        assertThat(candidate.getAddress4()).hasSize(35);
        assertThat(candidate.getAddress5()).hasSize(35);
    }

    @Test
    void a_fourth_dynamic_candidate_is_dropped_not_shifted_into_address4() {
        // organisation, sub-building, building name and number+street are all present here - four
        // dynamic candidates for only three slots (address1-3); the fourth (number+street) is
        // simply dropped, never spills into address4 (which is reserved for POST_TOWN).
        final Map<String, Object> dpa = new HashMap<>();
        dpa.put("UPRN", "10033544886");
        dpa.put("ORGANISATION_NAME", "HMCTS");
        dpa.put("SUB_BUILDING_NAME", "Flat 2");
        dpa.put("BUILDING_NAME", "Downing House");
        dpa.put("BUILDING_NUMBER", "10");
        dpa.put("THOROUGHFARE_NAME", "Downing Street");
        dpa.put("POST_TOWN", "LONDON");
        dpa.put("POSTCODE", "SW1A 1AA");

        final AddressCandidate candidate = CanonicalAddressMapper.toCandidate(dpa, false);

        assertThat(candidate.getAddress1()).isEqualTo("HMCTS");
        assertThat(candidate.getAddress2()).isEqualTo("Flat 2");
        assertThat(candidate.getAddress3()).isEqualTo("Downing House");
        assertThat(candidate.getAddress4()).isEqualTo("LONDON");
        assertThat(candidate.getAddress5()).isNull();
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

    @Test
    void maps_the_match_score_when_present() {
        final Map<String, Object> dpa = new HashMap<>();
        dpa.put("UPRN", "10033544886");
        dpa.put("BUILDING_NUMBER", "10");
        dpa.put("THOROUGHFARE_NAME", "Downing Street");
        dpa.put("POSTCODE", "SW1A 1AA");
        dpa.put("MATCH", "0.95");

        final AddressCandidate candidate = CanonicalAddressMapper.toCandidate(dpa, false);

        assertThat(candidate.getMatch()).isEqualByComparingTo(new BigDecimal("0.95"));
    }

    @Test
    void leaves_match_unset_when_absent() {
        final Map<String, Object> dpa = new HashMap<>();
        dpa.put("UPRN", "10033544886");
        dpa.put("BUILDING_NUMBER", "10");
        dpa.put("THOROUGHFARE_NAME", "Downing Street");
        dpa.put("POSTCODE", "SW1A 1AA");

        final AddressCandidate candidate = CanonicalAddressMapper.toCandidate(dpa, false);

        assertThat(candidate.getMatch()).isNull();
    }

    @Test
    void leaves_match_unset_when_unparseable() {
        final Map<String, Object> dpa = new HashMap<>();
        dpa.put("UPRN", "10033544886");
        dpa.put("BUILDING_NUMBER", "10");
        dpa.put("THOROUGHFARE_NAME", "Downing Street");
        dpa.put("POSTCODE", "SW1A 1AA");
        dpa.put("MATCH", "not-a-number");

        final AddressCandidate candidate = CanonicalAddressMapper.toCandidate(dpa, false);

        assertThat(candidate.getMatch()).isNull();
    }
}
