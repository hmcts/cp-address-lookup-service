package uk.gov.hmcts.cp.addresslookup.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.github.tomakehurst.wiremock.WireMockServer;

class StubOsPlacesConfigTest {

    private static final int STUB_PORT = 9561;

    private final StubOsPlacesConfig config = new StubOsPlacesConfig();

    @Test
    void refuses_to_start_when_key_is_required() {
        final OsPlacesClientProperties properties =
                new OsPlacesClientProperties("https://api.os.uk", null, 3000, 10_000, true);
        final StubProperties stubProperties = new StubProperties("ste");

        assertThatThrownBy(() -> config.stubOsPlacesServer(properties, stubProperties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("key-required=true");
    }

    @Test
    void starts_a_wiremock_server_on_the_fixed_stub_port_regardless_of_os_places_base_url() {
        // The stub deliberately ignores os-places.client.base-url (see StubOsPlacesConfig's own
        // Javadoc for why) - proving that here by passing a base-url pointing somewhere else
        // entirely and asserting the server still binds to the fixed stub port.
        final OsPlacesClientProperties properties =
                new OsPlacesClientProperties("https://api.os.uk", null, 3000, 10_000, false);
        final StubProperties stubProperties = new StubProperties("ste");

        final WireMockServer server = config.stubOsPlacesServer(properties, stubProperties);
        try {
            assertThat(server.isRunning()).isTrue();
            assertThat(server.port()).isEqualTo(STUB_PORT);
        } finally {
            server.stop();
        }
    }

    @Test
    void the_stub_rest_client_points_at_the_fixed_stub_port() {
        final RestClient restClient = config.stubOsPlacesRestClient();

        assertThatThrownBy(() -> restClient.get().uri("/anything").retrieve().toBodilessEntity())
                .satisfies(ex -> assertThat(ex.getMessage()).contains(String.valueOf(STUB_PORT)));
    }
}
