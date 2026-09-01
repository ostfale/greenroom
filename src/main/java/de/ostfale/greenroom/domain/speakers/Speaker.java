package de.ostfale.greenroom.domain.speakers;

import org.springframework.data.annotation.Id;

import java.util.List;

import static de.ostfale.greenroom.domain.Texts.optional;
import static de.ostfale.greenroom.domain.Texts.required;

/**
 * A person who gives talks. Kept for good: even after the last talk, the entry stays,
 * because the history of an evening points at it.
 *
 * <p>Everything here is the <em>current</em> state. What was announced for a particular
 * evening lives in the event and is never updated from this record again.
 */
public record Speaker(
        @Id Long id,
        String name,
        String company,
        String email,
        String phone,
        String bio,
        String notes,
        List<SpeakerLink> links) {

    public Speaker {
        name = required(name, "Speaker :: a speaker needs a name");
        email = required(email, "Speaker :: a speaker needs an email address");
        company = optional(company);
        phone = optional(phone);
        bio = optional(bio);
        notes = optional(notes);
        links = links == null ? List.of() : List.copyOf(links);
    }

    /** A new speaker, not yet stored. An inquiry needs a way out, so the address is not optional. */
    public static Speaker of(String name, String email) {
        return new Speaker(null, name, null, email, null, null, null, List.of());
    }

    public Speaker withBio(String newBio) {
        return new Speaker(id, name, company, email, phone, newBio, notes, links);
    }

    public Speaker withContact(String newCompany, String newEmail, String newPhone) {
        return new Speaker(id, name, newCompany, newEmail, newPhone, bio, notes, links);
    }

    public Speaker withLinks(List<SpeakerLink> newLinks) {
        return new Speaker(id, name, company, email, phone, bio, notes, newLinks);
    }
}
