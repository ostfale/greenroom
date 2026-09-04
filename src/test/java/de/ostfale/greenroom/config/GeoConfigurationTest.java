package de.ostfale.greenroom.config;

import de.ostfale.greenroom.adapter.out.geo.NoLookup;
import de.ostfale.greenroom.adapter.out.geo.NominatimLookup;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which of the two lookups an install gets. Every test in this project runs on the way
 * into {@link NoLookup} — nothing may leave the machine — so the way to the real one is
 * checked here, where no context is needed and no address is asked about.
 */
class GeoConfigurationTest {

    private static final String SERVICE = "https://nominatim.openstreetmap.org";
    private static final String WHO_WE_ARE = "greenroom (planung@example.org)";

    private final GeoConfiguration configuration = new GeoConfiguration();

    @Test
    void anInstallThatSwitchedTheLookupOnAsksTheService() {
        assertThat(configuration.addressLookup(true, SERVICE, WHO_WE_ARE))
                .isInstanceOf(NominatimLookup.class);
    }

    @Test
    void anInstallThatDidNotSwitchItOnAsksNobody() {
        assertThat(configuration.addressLookup(false, SERVICE, WHO_WE_ARE))
                .isInstanceOf(NoLookup.class);
    }

    /**
     * Nominatim asks callers to say who they are. An install that has not said so does not
     * ask at all, rather than asking anonymously and getting the address blocked.
     */
    @Test
    void withoutAnIdentifyingUserAgentNobodyIsAskedEither() {
        assertThat(configuration.addressLookup(true, SERVICE, "   "))
                .isInstanceOf(NoLookup.class);
    }
}
