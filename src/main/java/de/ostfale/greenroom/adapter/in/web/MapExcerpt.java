package de.ostfale.greenroom.adapter.in.web;

import de.ostfale.greenroom.domain.locations.Address;

import java.util.Locale;

/**
 * The address as a map, for the page to embed. Built here and not in the domain: a URL is
 * rendering, and an aggregate has no business knowing which map somebody looks at.
 */
final class MapExcerpt {

    /** Close enough to see the building, wide enough to see the street it is on. */
    private static final double EDGE = 0.004;

    private MapExcerpt() {
    }

    /**
     * OpenStreetMap's own embed, with a marker on the address. Null where the address was
     * never found — the page then shows no map rather than the middle of the ocean.
     */
    static String of(Address address) {
        if (address == null || !address.isLocated()) {
            return null;
        }
        double latitude = address.latitude();
        double longitude = address.longitude();
        return String.format(Locale.ROOT,
                "https://www.openstreetmap.org/export/embed.html"
                        + "?bbox=%.6f%%2C%.6f%%2C%.6f%%2C%.6f&layer=mapnik&marker=%.6f%%2C%.6f",
                longitude - EDGE, latitude - EDGE, longitude + EDGE, latitude + EDGE,
                latitude, longitude);
    }
}
