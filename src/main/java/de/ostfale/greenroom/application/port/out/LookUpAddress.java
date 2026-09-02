package de.ostfale.greenroom.application.port.out;

import de.ostfale.greenroom.domain.locations.Address;

import java.util.Optional;

/**
 * Turning a written address into a point on the map. The way out to whoever knows where
 * streets are — the application only knows that somebody does.
 *
 * <p>Not finding an address is an answer, not a failure: a note that says "Hamburg" and
 * nothing else cannot be placed, and the page then shows no map. Nothing else about the
 * location depends on it.
 */
public interface LookUpAddress {

    /** Where that address is, as far as anybody can tell. Empty when nobody can. */
    Optional<Position> find(Address address);

    /**
     * Whether there is anybody to ask at all. "Switched off" and "asked and not found" are
     * both an empty answer, and the page has to tell them apart — a button that silently
     * does nothing is worse than no button.
     */
    default boolean isAvailable() {
        return true;
    }

    /** A point on the planet. Latitude first, the way it is written everywhere. */
    record Position(double latitude, double longitude) {

        public Position {
            if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
                throw new IllegalArgumentException("Position :: that is not a point on this planet");
            }
        }
    }
}
