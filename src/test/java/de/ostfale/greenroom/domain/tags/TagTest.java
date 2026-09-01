package de.ostfale.greenroom.domain.tags;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Plain Java, no Spring. */
class TagTest {

    @Test
    void aTagNeedsAName() {
        assertThatThrownBy(() -> Tag.named("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
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
