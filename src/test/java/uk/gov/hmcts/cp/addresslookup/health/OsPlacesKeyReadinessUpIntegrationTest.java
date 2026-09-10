package uk.gov.hmcts.cp.addresslookup.health;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.annotation.Resource;

/**
 * Default configuration (key not required on this tier, per {@code os-places.client.key-required}
 * defaulting to false) - readiness must stay UP even with no OS Places key configured.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OsPlacesKeyReadinessUpIntegrationTest {

    @Resource
    private MockMvc mockMvc;

    @Test
    void readiness_is_up_when_key_not_required_on_this_tier() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.osPlacesKey.status").value("UP"));
    }
}
