package de.ostfale.greenroom.domain.locations;

import static de.ostfale.greenroom.domain.Texts.optional;

/**
 * Where a location can be found, and how many people fit in there. A place keeps more than
 * one address over the years — a company moves, or hosts at a second site — and the old one
 * is not deleted: an evening that was held there was held at that address, with that many
 * seats, not at today's.
 *
 * <p>Which of them counts now is what {@code active} says.
 */
public record Address(String street, String postalCode, String city, Integer capacity, boolean active) {

    public Address {
        street = optional(street);
        postalCode = optional(postalCode);
        city = optional(city);
        if (street == null && postalCode == null && city == null) {
            throw new IllegalArgumentException("Address :: an address needs a street or a town");
        }
        if (capacity != null && capacity <= 0) {
            throw new IllegalArgumentException("Address :: a capacity is a number of seats, not " + capacity);
        }
    }

    /** A newly agreed address — the one that counts from now on. */
    public static Address at(String street, String postalCode, String city) {
        return new Address(street, postalCode, city, null, true);
    }

    public Address withCapacity(Integer newCapacity) {
        return new Address(street, postalCode, city, newCapacity, active);
    }

    public Address activated() {
        return new Address(street, postalCode, city, capacity, true);
    }

    public Address deactivated() {
        return new Address(street, postalCode, city, capacity, false);
    }

    /** Street, postal code and town on one line — what a list or an invitation shows. */
    public String line() {
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
