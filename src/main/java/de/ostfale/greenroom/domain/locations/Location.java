package de.ostfale.greenroom.domain.locations;

import de.ostfale.greenroom.domain.Rule;
import de.ostfale.greenroom.domain.RuleViolated;
import org.springframework.data.annotation.Id;

import java.util.ArrayList;
import java.util.List;

import static de.ostfale.greenroom.domain.Texts.optional;
import static de.ostfale.greenroom.domain.Texts.required;
import static de.ostfale.greenroom.domain.Texts.url;

/**
 * A place that hosts an evening. It exists on its own: a location is entered once and used
 * again for years, independently of any event.
 *
 * <p>{@code inUse} is not {@link Address#active}. The address flag says where they are
 * now; this one says whether we still go there at all. A place that closed, moved away or
 * said no for good keeps every evening it ever hosted and every address it ever had — it
 * is only no longer offered when the next evening looks for a venue.
 *
 * <p>The address may still be missing — a host is often agreed before anybody has written
 * down the street — and it may be more than one: a place moves, or hosts at a second site.
 * The old address stays; only its {@code active} flag goes. The seat count belongs to the
 * address, because a place that moves rarely keeps the same room. What may never be missing is
 * somebody to ask, which is why there is at least one {@link ContactPerson} from the first
 * moment.
 *
 * <p>The website belongs to the place, not to the address: a company keeps its domain when
 * it moves, and there is one of them however many sites it hosts at.
 */
public record Location(
        @Id Long id,
        String name,
        String website,
        String notes,
        boolean inUse,
        List<Address> addresses,
        List<ContactPerson> contacts) {

    public Location {
        name = required(name, Rule.LOCATION_NEEDS_A_NAME);
        if (contacts == null || contacts.isEmpty()) {
            throw new RuleViolated(Rule.LOCATION_NEEDS_A_CONTACT);
        }
        website = url(website);
        notes = optional(notes);
        addresses = addresses == null ? List.of() : List.copyOf(addresses);
        contacts = List.copyOf(contacts);
    }

    /** A new location, not yet stored. The contact person comes with it, never later. */
    public static Location of(String name, ContactPerson contact) {
        return new Location(null, name, null, null, true, List.of(), List.of(contact));
    }

    /**
     * Whether the next evening may be planned here. Everything that already happened here
     * stays, addresses included — this only takes the place out of the choice.
     */
    public Location withInUse(boolean nowInUse) {
        return new Location(id, name, website, notes, nowInUse, addresses, contacts);
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
        Address address = addresses.get(reachable(position));
        List<Address> changed = new ArrayList<>(addresses);
        changed.set(position, active ? address.activated() : address.deactivated());
        return withAddresses(changed);
    }

    /**
     * How many fit in at that address. Somebody counted late, or miscounted — the seat
     * count is written down by hand and stays correctable, on a retired address too.
     *
     * <p>This is not the address being rewritten: street, town and position stay what they
     * were, so an evening that points here still points at where it was. Null takes the
     * count away again, back to nobody having counted.
     */
    public Location withCapacityAt(int position, Integer capacity) {
        Address address = addresses.get(reachable(position));
        List<Address> changed = new ArrayList<>(addresses);
        changed.set(position, address.withCapacity(capacity));
        return withAddresses(changed);
    }

    public Location withAddresses(List<Address> newAddresses) {
        return new Location(id, name, website, notes, inUse, newAddresses, contacts);
    }

    public Location withWebsite(String newWebsite) {
        return new Location(id, name, newWebsite, notes, inUse, addresses, contacts);
    }

    public Location withNotes(String newNotes) {
        return new Location(id, name, website, newNotes, inUse, addresses, contacts);
    }

    public Location withContacts(List<ContactPerson> newContacts) {
        return new Location(id, name, website, notes, inUse, addresses, newContacts);
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
     * @throws RuleViolated if it was the last one — a location nobody can be
     *                                  asked about is not a location we can use
     */
    public Location withContactRemoved(int position) {
        List<ContactPerson> left = new ArrayList<>(contacts);
        left.remove(known(position));
        return withContacts(left);
    }

    private int known(int position) {
        if (position < 0 || position >= contacts.size()) {
            throw new RuleViolated(Rule.NO_CONTACT_AT_POSITION, position);
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

    /**
     * The address an evening was at: the one at that position, or the one the place has
     * today when no position was written down.
     *
     * <p>A place with two addresses in use at once — two lecture halls, two rooms of
     * different size — has no "current" one to fall back on, and the page has to say so
     * rather than let {@link #currentAddress} pick whichever sits first.
     *
     * @throws RuleViolated if there is no address at that position
     */
    public Address addressAt(Integer position) {
        return position == null ? currentAddress() : addresses.get(reachable(position));
    }

    private int reachable(int position) {
        if (position < 0 || position >= addresses.size()) {
            throw new RuleViolated(Rule.NO_ADDRESS_AT_POSITION, position);
        }
        return position;
    }

    /** Whether the place offers several at once, so an evening has to name the one it used. */
    public boolean hasSeveralAddressesInUse() {
        return activeAddresses().size() > 1;
    }

    /** What a list shows: the current address on one line, empty while there is none. */
    public String addressLine() {
        Address current = currentAddress();
        return current == null ? "" : current.line();
    }
}
