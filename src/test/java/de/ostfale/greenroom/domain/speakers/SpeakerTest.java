package de.ostfale.greenroom.domain.speakers;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Plain Java, no Spring: that is the point of keeping the rules in the record. */
class SpeakerTest {

    @Test
    void aSpeakerNeedsAName() {
        assertThatThrownBy(() -> Speaker.of("  ", "max@example.org"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void aSpeakerNeedsAnEmailAddress() {
        assertThatThrownBy(() -> Speaker.of("Max Muster", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email");

        assertThatThrownBy(() -> Speaker.of("Max Muster", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email");
    }

    @Test
    void theAddressCannotBeTakenAwayAgain() {
        Speaker speaker = Speaker.of("Max Muster", "max@example.org");

        assertThatThrownBy(() -> speaker.withContact("Musterfirma GmbH", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email");
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
        assertThatThrownBy(() -> SpeakerLink.of(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
