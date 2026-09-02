package de.ostfale.greenroom.application.port.in;

import de.ostfale.greenroom.domain.locations.Address;
import de.ostfale.greenroom.domain.locations.ContactPerson;
import de.ostfale.greenroom.domain.locations.Location;

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

    /** Stores the changed fields of a location that is already known. */
    Location change(Location location);

    /**
     * The place moved or opened a second site. The earlier addresses are kept — an evening
     * held at an old address was held there.
     */
    Location addAddress(Long locationId, Address address, boolean replacesTheOthers);

    /**
     * Looks up where the address at that position is and keeps the answer with it. For the
     * addresses that were written down before anybody asked — and for a second try when
     * the lookup was unreachable.
     *
     * @throws IllegalArgumentException if there is no address at that position
     */
    Location locate(Long locationId, int position);

    /** Whether looking an address up is possible at all, or switched off in this install. */
    boolean canLocateAddresses();

    /** Turns the address at that position on or off. */
    Location setAddressActive(Long locationId, int position, boolean active);

    Location addContact(Long locationId, ContactPerson contact);

    Location changeContact(Long locationId, int position, ContactPerson contact);

    /**
     * @throws IllegalArgumentException if it was the last contact person — every location
     *                                  keeps somebody to ask
     */
    Location removeContact(Long locationId, int position);
}
