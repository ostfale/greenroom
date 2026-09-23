package de.ostfale.greenroom.adapter.in.web;

import de.ostfale.greenroom.application.port.in.ManageLocations;
import de.ostfale.greenroom.domain.Rule;
import de.ostfale.greenroom.domain.RuleViolated;
import de.ostfale.greenroom.domain.events.EventContact;
import de.ostfale.greenroom.domain.locations.Location;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Who a form ticked as asked for the room, taken from the place it ticked them at and
 * copied. The boxes send positions, and a position is only worth reading against the list
 * it was drawn in: a page left open while the place's contacts were edited sends numbers
 * that mean somebody else by now, and a form that names no place at all names nobody.
 *
 * <p>Beside {@link ChosenAddress} and for the same reason — the check needs the place
 * loaded, which is more than {@link FormValues} is allowed to know.
 */
@Component
class ChosenContacts {

    private final ManageLocations locations;

    ChosenContacts(ManageLocations locations) {
        this.locations = locations;
    }

    /**
     * The people at those positions, copied as the place has them now. Nothing ticked is
     * nobody named, which is what an evening at a place with a single contact stays at.
     *
     * @throws RuleViolated if there is no such place, or nobody at one of the positions
     */
    List<EventContact> of(Long place, List<String> positions) {
        if (place == null || positions == null || positions.isEmpty()) {
            return List.of();
        }
        Location host = locations.byId(place)
                .orElseThrow(() -> new RuleViolated(Rule.NOT_FOUND));
        List<EventContact> asked = new ArrayList<>();
        for (String position : positions) {
            if (position == null || position.isBlank()) {
                continue;
            }
            EventContact person = EventContact.copying(host.contactAt(number(position)));
            // A box cannot be ticked twice, so a repeat is a tampered form and not a wish.
            if (asked.stream().noneMatch(seen -> seen.email().equalsIgnoreCase(person.email()))) {
                asked.add(person);
            }
        }
        return asked;
    }

    private static int number(String position) {
        try {
            return Integer.parseInt(position.strip());
        } catch (NumberFormatException e) {
            throw new RuleViolated(Rule.NO_CONTACT_AT_POSITION, position);
        }
    }
}
