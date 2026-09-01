package de.ostfale.greenroom.domain.locations;

import static de.ostfale.greenroom.domain.Texts.optional;
import static de.ostfale.greenroom.domain.Texts.required;

/**
 * Who is asked when the evening is planned at this location — the host, the office, the
 * facility manager. Without an address there is no way to send the inquiry, so it is not
 * optional.
 */
public record ContactPerson(String name, String email, String phone) {

    public ContactPerson {
        name = required(name, "ContactPerson :: a contact person needs a name");
        email = required(email, "ContactPerson :: a contact person needs an email address");
        phone = optional(phone);
    }

    public static ContactPerson of(String name, String email) {
        return new ContactPerson(name, email, null);
    }
}
