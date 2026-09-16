package uk.gov.hmcts.cp.addresslookup.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.annotation.Resource;
import uk.gov.hmcts.cp.addresslookup.service.AddressSearchService;

/**
 * Proves that once {@code app.cors.allowed-origins} is explicitly restricted to specific
 * origin(s) (narrowing away from the {@code CorsConfig} default of {@code *}), only those origins
 * are allowed - a separate Spring context (distinct property) from
 * {@link AddressSearchControllerTest}'s default-configuration one.
 */
@SpringBootTest(properties = "app.cors.allowed-origins=http://localhost:4200")
@AutoConfigureMockMvc
class WebMvcConfigCorsTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:4200";

    @Resource
    private MockMvc mockMvc;

    @MockitoBean
    private AddressSearchService addressSearchService;

    @Test
    void allows_a_configured_origin() throws Exception {
        mockMvc.perform(options("/addresses/postcode")
                        .header("Origin", ALLOWED_ORIGIN)
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN));
    }

    @Test
    void rejects_an_origin_not_in_the_allowlist() throws Exception {
        mockMvc.perform(options("/addresses/postcode")
                        .header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }
}
