package uk.gov.hmcts.cp.addresslookup.controller;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.annotation.Resource;
import uk.gov.hmcts.cp.addresslookup.exception.DegradedModeException;
import uk.gov.hmcts.cp.addresslookup.service.AddressSearchService;
import uk.gov.hmcts.cp.openapi.model.al.AddressCandidate;
import uk.gov.hmcts.cp.openapi.model.al.AddressSearchResponse;
import uk.gov.hmcts.cp.openapi.model.al.DegradedReason;

@SpringBootTest
@AutoConfigureMockMvc
class AddressSearchControllerTest {

    private static final String MEDIA_TYPE = "application/vnd.addresslookup-service.addresses-postcode+json";
    private static final String ADDRESS_MEDIA_TYPE = "application/vnd.addresslookup-service.addresses+json";

    @Resource
    private MockMvc mockMvc;

    @MockitoBean
    private AddressSearchService addressSearchService;

    @Test
    void returns_200_with_candidates() throws Exception {
        final AddressCandidate candidate = new AddressCandidate("10", "SW1A 1AA", "10033544886")
                .address2("Downing Street");
        when(addressSearchService.searchByPostcode("SW1A 1AA", false))
                .thenReturn(new AddressSearchResponse(List.of(candidate)));

        mockMvc.perform(get("/addresses/postcode").param("postcode", "SW1A 1AA"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MEDIA_TYPE))
                .andExpect(jsonPath("$.results[0].address1").value("10"))
                .andExpect(jsonPath("$.results[0].address2").value("Downing Street"));
    }

    @Test
    void returns_200_with_empty_results_for_zero_matches() throws Exception {
        when(addressSearchService.searchByPostcode("ZZ99 1AA", false))
                .thenReturn(new AddressSearchResponse(List.of()));

        mockMvc.perform(get("/addresses/postcode").param("postcode", "ZZ99 1AA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results").isEmpty());
    }

    @Test
    void returns_400_when_postcode_is_missing() throws Exception {
        mockMvc.perform(get("/addresses/postcode"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("400"));
    }

    @Test
    void returns_400_when_postcode_exceeds_max_length() throws Exception {
        mockMvc.perform(get("/addresses/postcode").param("postcode", "12345678901"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void echoes_the_vendor_media_type_on_error_responses_too() throws Exception {
        mockMvc.perform(get("/addresses/postcode").header("Accept", MEDIA_TYPE))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MEDIA_TYPE));
    }

    @Test
    void returns_400_for_an_unrecognised_query_parameter() throws Exception {
        mockMvc.perform(get("/addresses/postcode")
                        .param("postcode", "SW1A 1AA")
                        .param("dataset", "LPI"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unrecognised query parameter 'dataset'"));
    }

    @Test
    void returns_503_degraded_when_os_places_is_unavailable() throws Exception {
        when(addressSearchService.searchByPostcode(anyString(), anyBoolean()))
                .thenThrow(new DegradedModeException(DegradedReason.UPSTREAM_TIMEOUT, null, "OS Places timed out"));

        mockMvc.perform(get("/addresses/postcode").param("postcode", "SW1A 1AA"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.degraded").value(true))
                .andExpect(jsonPath("$.reason").value("upstream-timeout"));
    }

    @Test
    void address_search_returns_200_with_candidates() throws Exception {
        final AddressCandidate candidate = new AddressCandidate("10", "SW1A 1AA", "10033544886")
                .address2("Downing Street");
        when(addressSearchService.searchByAddress("10 Downing Street", false))
                .thenReturn(new AddressSearchResponse(List.of(candidate)));

        mockMvc.perform(get("/addresses").param("address", "10 Downing Street"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(ADDRESS_MEDIA_TYPE))
                .andExpect(jsonPath("$.results[0].address1").value("10"))
                .andExpect(jsonPath("$.results[0].address2").value("Downing Street"));
    }

    @Test
    void address_search_returns_200_with_empty_results_for_zero_matches() throws Exception {
        when(addressSearchService.searchByAddress("nonsense", false))
                .thenReturn(new AddressSearchResponse(List.of()));

        mockMvc.perform(get("/addresses").param("address", "nonsense"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results").isEmpty());
    }

    @Test
    void address_search_returns_400_when_address_is_missing() throws Exception {
        mockMvc.perform(get("/addresses"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("400"));
    }

    @Test
    void address_search_returns_400_when_address_exceeds_max_length() throws Exception {
        mockMvc.perform(get("/addresses").param("address", "a".repeat(201)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void address_search_echoes_the_vendor_media_type_on_error_responses_too() throws Exception {
        mockMvc.perform(get("/addresses").header("Accept", ADDRESS_MEDIA_TYPE))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(ADDRESS_MEDIA_TYPE));
    }

    @Test
    void address_search_returns_400_for_an_unrecognised_query_parameter() throws Exception {
        mockMvc.perform(get("/addresses")
                        .param("address", "10 Downing Street")
                        .param("bbox", "1,2,3,4"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unrecognised query parameter 'bbox'"));
    }

    @Test
    void address_search_returns_503_degraded_when_os_places_is_unavailable() throws Exception {
        when(addressSearchService.searchByAddress(anyString(), anyBoolean()))
                .thenThrow(new DegradedModeException(DegradedReason.UPSTREAM_TIMEOUT, null, "OS Places timed out"));

        mockMvc.perform(get("/addresses").param("address", "10 Downing Street"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.degraded").value(true))
                .andExpect(jsonPath("$.reason").value("upstream-timeout"));
    }
}
