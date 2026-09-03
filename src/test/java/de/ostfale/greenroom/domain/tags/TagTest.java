package de.ostfale.greenroom.domain.tags;

import de.ostfale.greenroom.domain.Rule;
import org.junit.jupiter.api.Test;

import static de.ostfale.greenroom.Violations.ruleBrokenBy;
import static org.assertj.core.api.Assertions.assertThat;

/** Plain Java, no Spring. */
class TagTest {

    @Test
    void aTagNeedsAName() {
        assertThat(ruleBrokenBy(() -> Tag.named("  ")))
                .isEqualTo(Rule.TAG_NEEDS_A_NAME);
    }

    @Test
    void surroundingWhitespaceIsStripped() {
        assertThat(Tag.named("  Spring ").name()).isEqualTo("Spring");
    }

    @Test
    void twoTagsThatDifferOnlyInCaseAreTheSameTag() {
        assertThat(Tag.named("Spring").isSameAs(Tag.named("SPRING"))).isTrue();
        assertThat(Tag.named("Spring").isSameAs(Tag.named("Testing"))).isFalse();
        assertThat(Tag.named("Spring").isSameAs(null)).isFalse();
    }

    @Test
    void aNewTagHasNoIdYet() {
        assertThat(Tag.named("Spring").id()).isNull();
    }
}
