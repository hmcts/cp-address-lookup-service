package uk.gov.hmcts.cp.addresslookup.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StubPropertiesTest {

    @Test
    void accepts_each_allowed_fixture_set() {
        assertThat(new StubProperties("ste").fixtureSet()).isEqualTo("ste");
        assertThat(new StubProperties("dev").fixtureSet()).isEqualTo("dev");
        assertThat(new StubProperties("nft").fixtureSet()).isEqualTo("nft");
    }

    @Test
    void normalises_case_so_the_classpath_folder_lookup_never_mismatches() {
        assertThat(new StubProperties("STE").fixtureSet()).isEqualTo("ste");
    }

    @Test
    void rejects_an_unrecognised_fixture_set() {
        assertThatThrownBy(() -> new StubProperties("sit"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sit");
    }

    @Test
    void rejects_a_missing_fixture_set() {
        assertThatThrownBy(() -> new StubProperties(null))
                .isInstanceOf(IllegalStateException.class);
    }
}
