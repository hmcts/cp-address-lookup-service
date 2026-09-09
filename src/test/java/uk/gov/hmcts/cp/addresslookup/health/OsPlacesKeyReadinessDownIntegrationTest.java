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
 * Simulates an OS-backed tier (key-required=true) with no key configured - readiness must fail so
 * the pod is pulled out of traffic (not restarted) until a key is provisioned, with no redeploy.
 */
@SpringBootTest(properties = {
        "os-places.client.key-required=true",
        "os-places.client.api-key="
})
@AutoConfigureMockMvc
class OsPlacesKeyReadinessDownIntegrationTest {

    @Resource
    private MockMvc mockMvc;

    @Test
    void readiness_fails_when_key_required_and_absent() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.components.osPlacesKey.status").value("DOWN"));
    }
}
