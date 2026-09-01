package de.ostfale.greenroom.domain.locations;

import org.springframework.data.annotation.Id;

import java.util.ArrayList;
import java.util.List;

import static de.ostfale.greenroom.domain.Texts.optional;
import static de.ostfale.greenroom.domain.Texts.required;

/**
 * A place that hosts an evening. It exists on its own: a location is entered once and used
 * again for years, independently of any event.
 *
 * <p>The address may still be missing — a host is often agreed before anybody has written
 * down the street — and it may be more than one: a place moves, or hosts at a second site.
 * The old address stays; only its {@code active} flag goes. The seat count belongs to the
 * address, because a place that moves rarely keeps the same room. What may never be missing is
 * somebody to ask, which is why there is at least one {@link ContactPerson} from the first
 * moment.
 */
public record Location(
        @Id Long id,
        String name,
        String notes,
        List<Address> addresses,
        List<ContactPerson> contacts) {

    public Location {
        name = required(name, "Location :: a location needs a name");
        if (contacts == null || contacts.isEmpty()) {
            throw new IllegalArgumentException("Location :: a location needs at least one contact person");
        }
        notes = optional(notes);
        addresses = addresses == null ? List.of() : List.copyOf(addresses);
        contacts = List.copyOf(contacts);
    }

    /** A new location, not yet stored. The contact person comes with it, never later. */
    public static Location of(String name, ContactPerson contact) {
        return new Location(null, name, null, List.of(), List.of(contact));
    }

    /** The one address that counts from now on; whatever was there before is kept as past. */
    public Location withAddress(String street, String postalCode, String city) {
        return movedTo(Address.at(street, postalCode, city));
    }

    /**
     * The place moved. The new address is the active one, every earlier address stays on
     * record and goes quiet.
     */
    public Location movedTo(Address address) {
        List<Address> kept = new ArrayList<>(addresses.stream().map(Address::deactivated).toList());
        kept.add(address.activated());
        return withAddresses(kept);
    }

    /** A second site, without retiring the first. */
    public Location withAdditionalAddress(Address address) {
        List<Address> more = new ArrayList<>(addresses);
        more.add(address);
        return withAddresses(more);
    }

    /** Turns the address at that position on or off. */
    public Location withAddressActive(int position, boolean active) {
        if (position < 0 || position >= addresses.size()) {
            throw new IllegalArgumentException("Location :: there is no address at position " + position);
        }
        List<Address> changed = new ArrayList<>(addresses);
        Address address = changed.get(position);
        changed.set(position, active ? address.activated() : address.deactivated());
        return withAddresses(changed);
    }

    public Location withAddresses(List<Address> newAddresses) {
        return new Location(id, name, notes, newAddresses, contacts);
    }

    public Location withNotes(String newNotes) {
        return new Location(id, name, newNotes, addresses, contacts);
    }

    public Location withContacts(List<ContactPerson> newContacts) {
        return new Location(id, name, notes, addresses, newContacts);
    }

    public Location withAdditionalContact(ContactPerson contact) {
        List<ContactPerson> more = new ArrayList<>(contacts);
        more.add(contact);
        return withContacts(more);
    }

    /** Replaces the contact at that position — a new phone number, a new person. */
    public Location withContactChanged(int position, ContactPerson contact) {
        List<ContactPerson> changed = new ArrayList<>(contacts);
        changed.set(known(position), contact);
        return withContacts(changed);
    }

    /**
     * Drops the contact at that position.
     *
     * @throws IllegalArgumentException if it was the last one — a location nobody can be
     *                                  asked about is not a location we can use
     */
    public Location withContactRemoved(int position) {
        List<ContactPerson> left = new ArrayList<>(contacts);
        left.remove(known(position));
        return withContacts(left);
    }

    private int known(int position) {
        if (position < 0 || position >= contacts.size()) {
            throw new IllegalArgumentException("Location :: there is no contact person at position " + position);
        }
        return position;
    }

    /** Everything that counts right now — usually one, two when a place has two sites. */
    public List<Address> activeAddresses() {
        return addresses.stream().filter(Address::active).toList();
    }

    /** Where to go today, or {@code null} while nobody has written the address down. */
    public Address currentAddress() {
        return activeAddresses().stream().findFirst().orElse(null);
    }

    /** How many fit in today, or {@code null} while nobody has counted. */
    public Integer currentCapacity() {
        Address current = currentAddress();
        return current == null ? null : current.capacity();
    }

    /** What a list shows: the current address on one line, empty while there is none. */
    public String addressLine() {
        Address current = currentAddress();
        return current == null ? "" : current.line();
    }
}
