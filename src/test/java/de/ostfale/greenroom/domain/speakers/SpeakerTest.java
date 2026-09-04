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
}
