package de.ostfale.greenroom.domain.locations;

import de.ostfale.greenroom.domain.Rule;

import static de.ostfale.greenroom.domain.Texts.optional;
import static de.ostfale.greenroom.domain.Texts.required;

/**
 * Who is written to when the evening is planned at this location — the host, the office,
 * the facility manager. The address is what the page turns into a mail, so it is not
 * optional.
 */
public record ContactPerson(String name, String email, String phone) {

    public ContactPerson {
        name = required(name, Rule.CONTACT_NEEDS_A_NAME);
        email = required(email, Rule.CONTACT_NEEDS_AN_EMAIL);
        phone = optional(phone);
    }

    public static ContactPerson of(String name, String email) {
        return new ContactPerson(name, email, null);
    }
}
