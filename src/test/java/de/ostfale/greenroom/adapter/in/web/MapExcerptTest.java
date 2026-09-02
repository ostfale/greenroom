package de.ostfale.greenroom.adapter.in.web;

import de.ostfale.greenroom.domain.locations.Address;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Plain Java: the one thing a wrong map URL would show is the middle of the ocean. */
class MapExcerptTest {

    private static Address hamburg() {
        return Address.at("Musterweg 1", "22179", "Hamburg").at(53.5511, 9.9937);
    }

    @Test
    void anAddressThatWasFoundBecomesAMapAroundIt() {
        String url = MapExcerpt.of(hamburg());

        assertThat(url).startsWith("https://www.openstreetmap.org/export/embed.html");
        // Longitude first in the box, latitude first on the marker — that is how OSM reads it.
        assertThat(url).contains("bbox=9.989700%2C53.547100%2C9.997700%2C53.555100");
        assertThat(url).contains("marker=53.551100%2C9.993700");
    }

    /** A decimal point, whatever the machine's language says. */
    @Test
    void theNumbersDoNotFollowTheLocale() {
        assertThat(MapExcerpt.of(hamburg())).doesNotContain(",5");
    }

    @Test
    void anAddressNobodyFoundHasNoMap() {
        assertThat(MapExcerpt.of(Address.at(null, null, "Hamburg"))).isNull();
        assertThat(MapExcerpt.of(null)).isNull();
    }
}
