package de.ostfale.greenroom.domain.location;

import org.springframework.data.annotation.Id;

import java.util.List;

import static de.ostfale.greenroom.domain.Texts.optional;
import static de.ostfale.greenroom.domain.Texts.required;

/**
 * A place that hosts an evening. It exists on its own: a location is entered once and used
 * again for years, independently of any event.
 *
 * <p>The address may still be missing — a host is often agreed before anybody has written
 * down the street. What may never be missing is somebody to ask, which is why there is at
 * least one {@link ContactPerson} from the first moment.
 */
public record Location(
        @Id Long id,
        String name,
        String street,
        String postalCode,
        String city,
        Integer capacity,
        String notes,
        List<ContactPerson> contacts) {

    public Location {
        name = required(name, "Location :: a location needs a name");
        if (contacts == null || contacts.isEmpty()) {
            throw new IllegalArgumentException("Location :: a location needs at least one contact person");
        }
        if (capacity != null && capacity <= 0) {
            throw new IllegalArgumentException("Location :: a capacity is a number of seats, not " + capacity);
        }
        street = optional(street);
        postalCode = optional(postalCode);
        city = optional(city);
        notes = optional(notes);
        contacts = List.copyOf(contacts);
    }

    /** A new location, not yet stored. The contact person comes with it, never later. */
    public static Location of(String name, ContactPerson contact) {
        return new Location(null, name, null, null, null, null, null, List.of(contact));
    }

    public Location withAddress(String newStreet, String newPostalCode, String newCity) {
        return new Location(id, name, newStreet, newPostalCode, newCity, capacity, notes, contacts);
    }

    public Location withCapacity(Integer newCapacity) {
        return new Location(id, name, street, postalCode, city, newCapacity, notes, contacts);
    }

    public Location withNotes(String newNotes) {
        return new Location(id, name, street, postalCode, city, capacity, newNotes, contacts);
    }

    public Location withContacts(List<ContactPerson> newContacts) {
        return new Location(id, name, street, postalCode, city, capacity, notes, newContacts);
    }

    /** Street, postal code and town on one line — what a list or an invitation shows. */
    public String addressLine() {
        StringBuilder line = new StringBuilder();
        if (street != null) {
            line.append(street);
        }
        if (postalCode != null || city != null) {
            if (!line.isEmpty()) {
                line.append(", ");
            }
            line.append(postalCode == null ? city : city == null ? postalCode : postalCode + " " + city);
        }
        return line.toString();
    }
}
