package de.ostfale.greenroom;

import de.ostfale.greenroom.domain.events.Talk;
import de.ostfale.greenroom.domain.events.TalkSpeaker;
import de.ostfale.greenroom.domain.locations.Address;
import de.ostfale.greenroom.domain.locations.ContactPerson;
import de.ostfale.greenroom.domain.locations.Location;
import de.ostfale.greenroom.domain.speakers.Speaker;
import de.ostfale.greenroom.domain.tags.Tag;

import java.time.LocalDate;

/**
 * Valid objects to build a test on, so no test has to spell out the boring parts.
 *
 * <p>These belong in the <em>arrangement</em> of a test, never in its assertions: a test
 * that checks a stored name against {@code aSpeaker().name()} compares the fixture with
 * itself and pins nothing. Where the value is the point, write it out.
 *
 * <p>Everything here is minimal and valid. A test that needs more says so on the spot,
 * with the {@code with…} methods the records already have.
 */
public final class Fixtures {

    /** The evening every test that needs a date is planned for. */
    public static final LocalDate EVENING = LocalDate.of(2026, 9, 24);

    private Fixtures() {
    }

    public static Speaker aSpeaker() {
        return Speaker.of("Max Muster", "max@example.org");
    }

    public static ContactPerson aContact() {
        return ContactPerson.of("Max Muster", "max@example.org");
    }

    public static Location aLocation() {
        return Location.of("Musterfirma GmbH", aContact());
    }

    public static Address anAddress() {
        return Address.at("Musterweg 1", "22179", "Hamburg");
    }

    public static Tag aTag() {
        return Tag.named("Spring");
    }

    /** A talk that is nothing yet but the person we want to hear. */
    public static Talk aTalk(Long speakerId) {
        return Talk.by(TalkSpeaker.of(speakerId));
    }

    /** A talk that no longer holds an evening back: it has a title and an abstract. */
    public static Talk aReadyTalk(Long speakerId) {
        return aTalk(speakerId)
                .withTitle("Records in Java 25")
                .withAbstract("Warum Records mehr sind als weniger Tippen.");
    }
}
