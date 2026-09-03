package de.ostfale.greenroom.domain.locations;

import de.ostfale.greenroom.domain.Rule;
import de.ostfale.greenroom.domain.RuleViolated;

import static de.ostfale.greenroom.domain.Texts.optional;

/**
 * Where a location can be found, and how many people fit in there. A place keeps more than
 * one address over the years — a company moves, or hosts at a second site — and the old one
 * is not deleted: an evening that was held there was held at that address, with that many
 * seats, not at today's.
 *
 * <p>Which of them counts now is what {@code active} says.
 *
 * <p>The position is looked up once from the written address and kept with it. It belongs
 * here rather than on the location for the same reason the seat count does: an old address
 * points at where that evening was, not at where the host sits today. Where the address is
 * too thin to find, there is none, and the page simply shows no map.
 */
public record Address(String street, String postalCode, String city, Integer capacity,
                      boolean active, Double latitude, Double longitude) {

    public Address {
        street = optional(street);
        postalCode = optional(postalCode);
        city = optional(city);
        if (street == null && postalCode == null && city == null) {
            throw new RuleViolated(Rule.ADDRESS_NEEDS_A_STREET_OR_TOWN);
        }
        if (capacity != null && capacity <= 0) {
            throw new RuleViolated(Rule.CAPACITY_IS_A_NUMBER_OF_SEATS, capacity);
        }
        if ((latitude == null) != (longitude == null)) {
            throw new RuleViolated(Rule.POSITION_IS_HALF);
        }
        if (latitude != null && (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180)) {
            throw new RuleViolated(Rule.POSITION_OFF_THE_PLANET);
        }
    }

    /** A newly agreed address — the one that counts from now on. */
    public static Address at(String street, String postalCode, String city) {
        return new Address(street, postalCode, city, null, true, null, null);
    }

    public Address withCapacity(Integer newCapacity) {
        return new Address(street, postalCode, city, newCapacity, active, latitude, longitude);
    }

    public Address activated() {
        return new Address(street, postalCode, city, capacity, true, latitude, longitude);
    }

    public Address deactivated() {
        return new Address(street, postalCode, city, capacity, false, latitude, longitude);
    }

    /** Where this address turned out to be. Null takes the position away again. */
    public Address at(Double newLatitude, Double newLongitude) {
        return new Address(street, postalCode, city, capacity, active, newLatitude, newLongitude);
    }

    /** Whether there is a point to put on a map. */
    public boolean isLocated() {
        return latitude != null;
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
