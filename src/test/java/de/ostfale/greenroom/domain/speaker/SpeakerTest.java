package de.ostfale.greenroom.domain.speaker;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Plain Java, no Spring: that is the point of keeping the rules in the record. */
class SpeakerTest {

    @Test
    void aSpeakerNeedsAName() {
        assertThatThrownBy(() -> Speaker.named("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void blankOptionalFieldsBecomeNull() {
        Speaker speaker = Speaker.named("Max Muster").withContact("  ", "", null);

        assertThat(speaker.company()).isNull();
        assertThat(speaker.email()).isNull();
        assertThat(speaker.phone()).isNull();
    }

    @Test
    void surroundingWhitespaceIsStripped() {
        assertThat(Speaker.named("  Max Muster ").name()).isEqualTo("Max Muster");
    }

    @Test
    void withoutAMailAddressNobodyCanBeAsked() {
        assertThat(Speaker.named("Max Muster").isReachable()).isFalse();
        assertThat(Speaker.named("Max Muster")
                .withContact(null, "max@example.org", null)
                .isReachable()).isTrue();
    }

    @Test
    void linksAreNeverNullAndNeverSharedWithTheCaller() {
        assertThat(Speaker.named("Max Muster").links()).isEmpty();

        List<SpeakerLink> mutable = new ArrayList<>(List.of(SpeakerLink.of("https://example.org")));
        Speaker speaker = Speaker.named("Max Muster").withLinks(mutable);
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
