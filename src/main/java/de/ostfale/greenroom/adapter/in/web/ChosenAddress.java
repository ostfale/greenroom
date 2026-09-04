package de.ostfale.greenroom.adapter.in.web;

import de.ostfale.greenroom.application.port.in.ManageLocations;
import de.ostfale.greenroom.domain.Rule;
import de.ostfale.greenroom.domain.RuleViolated;
import org.springframework.stereotype.Component;

/**
 * Which of a venue's addresses a form picked. Two forms ask that — the one that gives an
 * evening its host and the one that writes down a past evening — and both have to check
 * the answer against the place it points into: a position that is not there is a stale
 * page or a tampered form, and either way not an address.
 *
 * <p>Its own component rather than one more method in {@link FormValues}, because the
 * check needs the place loaded. That class turns what a form sends into what a record
 * accepts and asks nobody anything; this one asks.
 */
@Component
class ChosenAddress {

    private final ManageLocations locations;

    ChosenAddress(ManageLocations locations) {
        this.locations = locations;
    }

    /**
     * The position the form picked, or {@code null} for the address the place has today.
     * A form that names no place at all means the same: a position points into one place's
     * list, and at another the same number is another building.
     *
     * <p>Asked here so it refuses on the form rather than later, on the page that reads
     * the position back.
     *
     * @throws RuleViolated if there is no such place, or no address at that position
     */
    Integer of(Long place, String position) {
        if (place == null || position == null || position.isBlank()) {
            return null;
        }
        int picked;
        try {
            picked = Integer.parseInt(position.strip());
        } catch (NumberFormatException e) {
            throw new RuleViolated(Rule.NO_ADDRESS_AT_POSITION, position);
        }
        locations.byId(place).orElseThrow(() -> new RuleViolated(Rule.NOT_FOUND))
                .addressAt(picked);
        return picked;
    }
}
