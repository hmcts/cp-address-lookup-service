package uk.gov.hmcts.cp.addresslookup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import uk.gov.hmcts.cp.addresslookup.client.OsPlacesClient;
import uk.gov.hmcts.cp.openapi.model.al.AddressSearchResponse;

class AddressSearchServiceImplTest {

    private OsPlacesClient osPlacesClient;
    private AddressSearchServiceImpl service;

    @BeforeEach
    void setUp() {
        osPlacesClient = mock(OsPlacesClient.class);
        service = new AddressSearchServiceImpl(osPlacesClient);
    }

    @Test
    void maps_client_results_into_a_search_response() {
        final Map<String, Object> dpa = new HashMap<>();
        dpa.put("UPRN", "10033544886");
        dpa.put("BUILDING_NUMBER", "10");
        dpa.put("THOROUGHFARE_NAME", "Downing Street");
        dpa.put("POSTCODE", "SW1A 1AA");
        when(osPlacesClient.searchByPostcode("SW1A 1AA")).thenReturn(List.of(dpa));

        final AddressSearchResponse response = service.searchByPostcode("SW1A 1AA", false);

        assertThat(response.getResults()).hasSize(1);
        assertThat(response.getResults().get(0).getAddress1()).isEqualTo("10 Downing Street");
        assertThat(response.getResults().get(0).getDpa()).isNull();
    }

    @Test
    void returns_empty_results_for_zero_matches() {
        when(osPlacesClient.searchByPostcode("ZZ99 1AA")).thenReturn(List.of());

        final AddressSearchResponse response = service.searchByPostcode("ZZ99 1AA", false);

        assertThat(response.getResults()).isEmpty();
    }

    @Test
    void trims_the_postcode_before_calling_the_client() {
        when(osPlacesClient.searchByPostcode(eq("SW1A 1AA"))).thenReturn(List.of());

        service.searchByPostcode(" SW1A 1AA ", false);

        verify(osPlacesClient).searchByPostcode("SW1A 1AA");
    }

    @Test
    void address_search_maps_client_results_into_a_search_response() {
        final Map<String, Object> dpa = new HashMap<>();
        dpa.put("UPRN", "10033544886");
        dpa.put("BUILDING_NUMBER", "10");
        dpa.put("THOROUGHFARE_NAME", "Downing Street");
        dpa.put("POSTCODE", "SW1A 1AA");
        when(osPlacesClient.searchByAddress("10 Downing Street")).thenReturn(List.of(dpa));

        final AddressSearchResponse response = service.searchByAddress("10 Downing Street", false);

        assertThat(response.getResults()).hasSize(1);
        assertThat(response.getResults().get(0).getAddress1()).isEqualTo("10 Downing Street");
    }

    @Test
    void address_search_returns_empty_results_for_zero_matches() {
        when(osPlacesClient.searchByAddress("nonsense")).thenReturn(List.of());

        final AddressSearchResponse response = service.searchByAddress("nonsense", false);

        assertThat(response.getResults()).isEmpty();
    }

    @Test
    void address_search_passes_the_address_through_unmodified() {
        when(osPlacesClient.searchByAddress(eq(" 10 Downing Street "))).thenReturn(List.of());

        service.searchByAddress(" 10 Downing Street ", false);

        verify(osPlacesClient).searchByAddress(" 10 Downing Street ");
    }

    @Test
    void find_match_maps_the_best_candidate_with_its_score() {
        final Map<String, Object> dpa = new HashMap<>();
        dpa.put("UPRN", "10033544886");
        dpa.put("BUILDING_NUMBER", "10");
        dpa.put("THOROUGHFARE_NAME", "Downing Street");
        dpa.put("POSTCODE", "SW1A 1AA");
        dpa.put("MATCH", "0.95");
        when(osPlacesClient.findBestMatch("10 Downing Street", new BigDecimal("0.7"))).thenReturn(List.of(dpa));

        final AddressSearchResponse response = service.findMatch("10 Downing Street", new BigDecimal("0.7"));

        assertThat(response.getResults()).hasSize(1);
        assertThat(response.getResults().get(0).getMatch()).isEqualByComparingTo(new BigDecimal("0.95"));
        assertThat(response.getResults().get(0).getDpa()).isNull();
    }

    @Test
    void find_match_returns_empty_results_for_a_nonsense_address() {
        when(osPlacesClient.findBestMatch("gibberish", new BigDecimal("0.7"))).thenReturn(List.of());

        final AddressSearchResponse response = service.findMatch("gibberish", new BigDecimal("0.7"));

        assertThat(response.getResults()).isEmpty();
    }

    @Test
    void find_match_caps_to_at_most_one_candidate_even_if_the_client_returns_more() {
        final Map<String, Object> first = new HashMap<>();
        first.put("UPRN", "10033544886");
        first.put("BUILDING_NUMBER", "10");
        first.put("THOROUGHFARE_NAME", "Downing Street");
        first.put("POSTCODE", "SW1A 1AA");
        final Map<String, Object> second = new HashMap<>();
        second.put("UPRN", "10033544887");
        second.put("BUILDING_NUMBER", "11");
        second.put("THOROUGHFARE_NAME", "Downing Street");
        second.put("POSTCODE", "SW1A 1AA");
        when(osPlacesClient.findBestMatch("10 Downing Street", null)).thenReturn(List.of(first, second));

        final AddressSearchResponse response = service.findMatch("10 Downing Street", null);

        assertThat(response.getResults()).hasSize(1);
    }

    @Test
    void find_match_passes_min_match_through_unmodified() {
        when(osPlacesClient.findBestMatch(eq("10 Downing Street"), eq(new BigDecimal("0.9")))).thenReturn(List.of());

        service.findMatch("10 Downing Street", new BigDecimal("0.9"));

        verify(osPlacesClient).findBestMatch("10 Downing Street", new BigDecimal("0.9"));
    }
}
