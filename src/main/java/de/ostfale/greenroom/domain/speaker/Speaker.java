package de.ostfale.greenroom.domain.speaker;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

import java.util.List;

/**
 * A person who gives talks. Kept for good: even after the last talk, the entry stays,
 * because the history of an evening points at it.
 *
 * <p>Everything here is the <em>current</em> state. What was announced for a particular
 * evening lives in the event and is never updated from this record again.
 */
@Table("speaker")
public record Speaker(
        @Id Long id,
        String name,
        String company,
        String email,
        String phone,
        String bio,
        String notes,
        @MappedCollection(idColumn = "speaker_id", keyColumn = "position") List<SpeakerLink> links) {

    public Speaker {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("a speaker needs a name");
        }
        name = name.strip();
        company = blankToNull(company);
        email = blankToNull(email);
        phone = blankToNull(phone);
        bio = blankToNull(bio);
        notes = blankToNull(notes);
        links = links == null ? List.of() : List.copyOf(links);
    }

    /** A new speaker, not yet stored. */
    public static Speaker named(String name) {
        return new Speaker(null, name, null, null, null, null, null, List.of());
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

    /** Whether we can write to this person at all — an inquiry needs a way out. */
    public boolean isReachable() {
        return email != null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
