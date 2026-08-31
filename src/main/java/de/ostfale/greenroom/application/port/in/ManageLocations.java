package de.ostfale.greenroom.application.port.in;

import de.ostfale.greenroom.domain.location.Location;

import java.util.List;

/** Everything the web adapter needs to keep the list of locations. */
public interface ManageLocations {

    List<Location> all();

    /** Locations whose name or town contains the fragment; all of them if it is blank. */
    List<Location> matching(String fragment);

    /** Stores a location that has no id yet and returns it with the id it was given. */
    Location add(Location location);
}
