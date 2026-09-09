package de.ostfale.greenroom.domain.speakers;

import de.ostfale.greenroom.domain.Rule;
import de.ostfale.greenroom.domain.RuleViolated;
import org.springframework.data.annotation.Id;

import java.util.ArrayList;
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
        name = required(name, Rule.SPEAKER_NEEDS_A_NAME);
        email = required(email, Rule.SPEAKER_NEEDS_AN_EMAIL);
        company = optional(company);
        phone = optional(phone);
        bio = optional(bio);
        notes = optional(notes);
        links = links == null ? List.of() : List.copyOf(links);
    }

    /** A new speaker, not yet stored. There is no writing to somebody without an address. */
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

    /** One more place the speaker can be found. */
    public Speaker withAdditionalLink(SpeakerLink link) {
        List<SpeakerLink> more = new ArrayList<>(links);
        more.add(link);
        return withLinks(more);
    }

    /** Replaces the link at that position — a moved blog, a label put right. */
    public Speaker withLinkChanged(int position, SpeakerLink link) {
        List<SpeakerLink> changed = new ArrayList<>(links);
        changed.set(known(position), link);
        return withLinks(changed);
    }

    /** Drops the link at that position. A speaker without any is nothing unusual. */
    public Speaker withLinkRemoved(int position) {
        List<SpeakerLink> left = new ArrayList<>(links);
        left.remove(known(position));
        return withLinks(left);
    }

    private int known(int position) {
        if (position < 0 || position >= links.size()) {
            throw new RuleViolated(Rule.NO_LINK_AT_POSITION, position);
        }
        return position;
    }
}
