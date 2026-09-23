package de.ostfale.greenroom.domain.events;

import de.ostfale.greenroom.domain.Rule;
import de.ostfale.greenroom.domain.locations.ContactPerson;

import static de.ostfale.greenroom.domain.Texts.optional;
import static de.ostfale.greenroom.domain.Texts.required;

/**
 * Who was asked for the room for this one evening. A place keeps a list of people to write
 * to, and over the years that list changes — somebody leaves, somebody else takes over. The
 * evening is not interested in who holds the job today but in who held it then, so the
 * person is copied here when they are picked and never read back from the place again.
 *
 * <p>A copy and not a position into the venue's list, unlike the address: an address is
 * never rewritten and never dropped, which is what makes pointing at one safe. A contact is
 * edited and removed, so a stored position would quietly come to mean somebody else.
 *
 * <p>Its own record rather than a {@link ContactPerson} on the event, because the two are
 * stored apart: the place's list is the place's, this is the evening's, and one table
 * holding both would say a row belongs to whichever of the two happens to be filled in.
 */
public record EventContact(String name, String email, String phone) {

    public EventContact {
        name = required(name, Rule.CONTACT_NEEDS_A_NAME);
        email = required(email, Rule.CONTACT_NEEDS_AN_EMAIL);
        phone = optional(phone);
    }

    /** The person as the place has them right now — this is the copy being made. */
    public static EventContact copying(ContactPerson person) {
        return new EventContact(person.name(), person.email(), person.phone());
    }

    /** Whether this is the same person the place lists, told apart by the mail address. */
    public boolean isSameAs(ContactPerson person) {
        return person != null && email.equalsIgnoreCase(person.email());
    }
}
