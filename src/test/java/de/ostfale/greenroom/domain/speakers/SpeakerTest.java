package de.ostfale.greenroom.domain.speakers;

import de.ostfale.greenroom.domain.Rule;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static de.ostfale.greenroom.Violations.ruleBrokenBy;
import static org.assertj.core.api.Assertions.assertThat;

/** Plain Java, no Spring: that is the point of keeping the rules in the record. */
class SpeakerTest {

    @Test
    void aSpeakerNeedsAName() {
        assertThat(ruleBrokenBy(() -> Speaker.of("  ", "max@example.org")))
                .isEqualTo(Rule.SPEAKER_NEEDS_A_NAME);
    }

    @Test
    void aSpeakerNeedsAnEmailAddress() {
        assertThat(ruleBrokenBy(() -> Speaker.of("Max Muster", null)))
                .isEqualTo(Rule.SPEAKER_NEEDS_AN_EMAIL);

        assertThat(ruleBrokenBy(() -> Speaker.of("Max Muster", " ")))
                .isEqualTo(Rule.SPEAKER_NEEDS_AN_EMAIL);
    }

    @Test
    void theAddressCannotBeTakenAwayAgain() {
        Speaker speaker = Speaker.of("Max Muster", "max@example.org");

        assertThat(ruleBrokenBy(() -> speaker.withContact("Musterfirma GmbH", null, null)))
                .isEqualTo(Rule.SPEAKER_NEEDS_AN_EMAIL);
    }

    @Test
    void blankOptionalFieldsBecomeNull() {
        Speaker speaker = Speaker.of("Max Muster", "max@example.org")
                .withContact("  ", "max@example.org", "");

        assertThat(speaker.company()).isNull();
        assertThat(speaker.phone()).isNull();
    }

    @Test
    void surroundingWhitespaceIsStripped() {
        Speaker speaker = Speaker.of("  Max Muster ", " max@example.org ");

        assertThat(speaker.name()).isEqualTo("Max Muster");
        assertThat(speaker.email()).isEqualTo("max@example.org");
    }

    @Test
    void linksAreNeverNullAndNeverSharedWithTheCaller() {
        assertThat(Speaker.of("Max Muster", "max@example.org").links()).isEmpty();
        // A stored row with nothing beside it comes back through the canonical constructor.
        assertThat(new Speaker(1L, "Max Muster", null, "max@example.org", null, null, null, null)
                .links()).isEmpty();

        List<SpeakerLink> mutable = new ArrayList<>(List.of(SpeakerLink.of("https://example.org")));
        Speaker speaker = Speaker.of("Max Muster", "max@example.org").withLinks(mutable);
        mutable.clear();

        assertThat(speaker.links()).hasSize(1);
    }

    @Test
    void aLinkFallsBackToItsUrlWhenItHasNoLabel() {
        assertThat(SpeakerLink.of("https://example.org").display()).isEqualTo("https://example.org");
        assertThat(new SpeakerLink("https://example.org", "Blog").display()).isEqualTo("Blog");
    }

    @Test
    void aLinkNeedsAUrl() {
        assertThat(ruleBrokenBy(() -> SpeakerLink.of(" ")))
                .isEqualTo(Rule.SPEAKER_LINK_NEEDS_A_URL);
    }

    /** A homepage is written down as "www.…", and an href without a scheme goes nowhere. */
    @Test
    void aLinkWrittenWithoutASchemeGetsOne() {
        assertThat(SpeakerLink.of("www.max-muster.de").url()).isEqualTo("https://www.max-muster.de");
        assertThat(SpeakerLink.of("http://max-muster.de").url()).isEqualTo("http://max-muster.de");
        assertThat(SpeakerLink.of("https://max-muster.de").url()).isEqualTo("https://max-muster.de");
    }

    @Test
    void linksAreAddedChangedAndDroppedOneAtATime() {
        Speaker speaker = Speaker.of("Max Muster", "max@example.org")
                .withAdditionalLink(SpeakerLink.of("www.max-muster.de"))
                .withAdditionalLink(new SpeakerLink("www.example.org/talk", "Aufzeichnung"));

        assertThat(speaker.links()).hasSize(2);

        speaker = speaker.withLinkChanged(0, new SpeakerLink("www.max-muster.de", "Blog"));
        assertThat(speaker.links().getFirst().display()).isEqualTo("Blog");

        speaker = speaker.withLinkRemoved(0);
        assertThat(speaker.links()).singleElement()
                .extracting(SpeakerLink::display).isEqualTo("Aufzeichnung");
    }

    @Test
    void aLinkThatIsNotThereCannotBeChangedOrDropped() {
        Speaker speaker = Speaker.of("Max Muster", "max@example.org")
                .withAdditionalLink(SpeakerLink.of("www.max-muster.de"));

        assertThat(ruleBrokenBy(() -> speaker.withLinkRemoved(1)))
                .isEqualTo(Rule.NO_LINK_AT_POSITION);
        assertThat(ruleBrokenBy(() -> speaker.withLinkChanged(-1, SpeakerLink.of("www.example.org"))))
                .isEqualTo(Rule.NO_LINK_AT_POSITION);
    }
}
