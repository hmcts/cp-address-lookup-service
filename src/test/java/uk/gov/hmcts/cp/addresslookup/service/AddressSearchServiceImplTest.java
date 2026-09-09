package uk.gov.hmcts.cp.addresslookup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        assertThat(response.getResults().get(0).getAddress1()).isEqualTo("10");
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
}
