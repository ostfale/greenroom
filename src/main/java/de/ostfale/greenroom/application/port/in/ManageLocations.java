package de.ostfale.greenroom.application.port.in;

import de.ostfale.greenroom.domain.location.Address;
import de.ostfale.greenroom.domain.location.Location;

import java.util.List;
import java.util.Optional;

/** Everything the web adapter needs to keep the list of locations. */
public interface ManageLocations {

    List<Location> all();

    /** Locations whose name or town contains the fragment; all of them if it is blank. */
    List<Location> matching(String fragment);

    Optional<Location> byId(Long id);

    /** Stores a location that has no id yet and returns it with the id it was given. */
    Location add(Location location);

    /**
     * The place moved or opened a second site. The earlier addresses are kept — an evening
     * held at an old address was held there.
     */
    Location addAddress(Long locationId, Address address, boolean replacesTheOthers);

    /** Turns the address at that position on or off. */
    Location setAddressActive(Long locationId, int position, boolean active);
}
