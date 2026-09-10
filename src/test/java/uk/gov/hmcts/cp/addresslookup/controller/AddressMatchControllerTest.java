package uk.gov.hmcts.cp.addresslookup.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
class AddressMatchControllerTest {

    private static final String MEDIA_TYPE = "application/vnd.addresslookup-service.addresses-find+json";

    @Resource
    private MockMvc mockMvc;

    @MockitoBean
    private AddressSearchService addressSearchService;

    @Test
    void returns_200_with_at_most_one_candidate_and_its_score() throws Exception {
        final AddressCandidate candidate = new AddressCandidate("10", "SW1A 1AA", "10033544886")
                .address2("Downing Street")
                .match(new BigDecimal("0.95"));
        when(addressSearchService.findMatch("10 Downing Street", new BigDecimal("0.7")))
                .thenReturn(new AddressSearchResponse(List.of(candidate)));

        mockMvc.perform(get("/addresses/find")
                        .param("address", "10 Downing Street")
                        .param("minMatch", "0.7"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MEDIA_TYPE))
                .andExpect(jsonPath("$.results[0].address1").value("10"))
                .andExpect(jsonPath("$.results[0].match").value(0.95));
    }

    @Test
    void returns_200_with_empty_results_for_a_nonsense_address() throws Exception {
        when(addressSearchService.findMatch("gibberish nonsense", new BigDecimal("0.7")))
                .thenReturn(new AddressSearchResponse(List.of()));

        mockMvc.perform(get("/addresses/find")
                        .param("address", "gibberish nonsense")
                        .param("minMatch", "0.7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results").isEmpty());
    }

    @Test
    void minmatch_is_optional() throws Exception {
        when(addressSearchService.findMatch("10 Downing Street", null))
                .thenReturn(new AddressSearchResponse(List.of()));

        mockMvc.perform(get("/addresses/find").param("address", "10 Downing Street"))
                .andExpect(status().isOk());
    }

    @Test
    void returns_400_when_address_is_missing() throws Exception {
        mockMvc.perform(get("/addresses/find").param("minMatch", "0.7"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("400"));
    }

    @Test
    void returns_400_when_address_exceeds_max_length() throws Exception {
        mockMvc.perform(get("/addresses/find").param("address", "a".repeat(201)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns_400_when_min_match_is_below_the_floor() throws Exception {
        mockMvc.perform(get("/addresses/find")
                        .param("address", "10 Downing Street")
                        .param("minMatch", "0.05"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns_400_when_min_match_exceeds_one() throws Exception {
        mockMvc.perform(get("/addresses/find")
                        .param("address", "10 Downing Street")
                        .param("minMatch", "1.5"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void echoes_the_vendor_media_type_on_error_responses_too() throws Exception {
        mockMvc.perform(get("/addresses/find").header("Accept", MEDIA_TYPE))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MEDIA_TYPE));
    }

    @Test
    void returns_400_for_an_unrecognised_query_parameter() throws Exception {
        mockMvc.perform(get("/addresses/find")
                        .param("address", "10 Downing Street")
                        .param("matchprecision", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unrecognised query parameter 'matchprecision'"));
    }

    @Test
    void returns_503_degraded_when_os_places_is_unavailable() throws Exception {
        when(addressSearchService.findMatch(anyString(), any()))
                .thenThrow(new DegradedModeException(DegradedReason.UPSTREAM_TIMEOUT, null, "OS Places timed out"));

        mockMvc.perform(get("/addresses/find").param("address", "10 Downing Street"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.degraded").value(true))
                .andExpect(jsonPath("$.reason").value("upstream-timeout"));
    }
}
