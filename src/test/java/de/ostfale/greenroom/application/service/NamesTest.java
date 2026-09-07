package de.ostfale.greenroom.application.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/** Plain Java: the order names are read in does not need a database to be decided. */
class NamesTest {

    @Test
    void aSmallFirstLetterIsNotTheEndOfTheAlphabet() {
        assertThat(sorted("Tchibo", "adesso", "Zeise Kinos", "brainbits"))
                .containsExactly("adesso", "brainbits", "Tchibo", "Zeise Kinos");
    }

    @Test
    void anUmlautStandsWithItsPlainLetter() {
        assertThat(sorted("Zeise Kinos", "Körber", "Otto", "Adobe"))
                .containsExactly("Adobe", "Körber", "Otto", "Zeise Kinos");
    }

    /** Case decides nothing until two names are otherwise the same — then it decides. */
    @Test
    void twoSpellingsOfOneNameStayNextToEachOther() {
        assertThat(sorted("adesso", "Bosch", "Adesso"))
                .containsExactly("adesso", "Adesso", "Bosch");
    }

    @Test
    void anEmptyListStaysEmpty() {
        assertThat(Names.sorted(List.<String>of(), Function.identity())).isEmpty();
    }

    private static List<String> sorted(String... names) {
        return Names.sorted(List.of(names), Function.identity());
    }
}
