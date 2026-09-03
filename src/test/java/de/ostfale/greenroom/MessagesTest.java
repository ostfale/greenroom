package de.ostfale.greenroom;

import de.ostfale.greenroom.domain.Rule;
import de.ostfale.greenroom.domain.activities.ActivityKind;
import de.ostfale.greenroom.domain.events.EventMode;
import de.ostfale.greenroom.domain.events.EventStatus;
import de.ostfale.greenroom.domain.events.NextStep;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every name the domain uses has a German word on the page. The domain refuses with a
 * {@link Rule} and the templates print enum constants through the bundle, so a constant
 * without a key is a page that shows a placeholder — or, for a rule, an exception on top
 * of the refusal it was meant to explain.
 *
 * <p>This is the test the string matching in the controllers did not have: it fails when
 * a constant is added and its sentence is forgotten, which is the whole point of naming
 * refusals instead of writing them out.
 */
class MessagesTest {

    private static final Properties TEXTS = load();

    @Test
    void everyRuleHasAGermanSentence() {
        assertThat(Rule.values()).allSatisfy(rule ->
                assertThat(TEXTS).containsKey("rule." + rule));
    }

    @Test
    void everyEnumShownOnAPageHasAGermanWord() {
        assertThat(EventStatus.values()).allSatisfy(status ->
                assertThat(TEXTS).containsKey("event.status." + status));
        assertThat(EventMode.values()).allSatisfy(mode ->
                assertThat(TEXTS).containsKey("event.mode." + mode));
        assertThat(NextStep.values()).allSatisfy(step ->
                assertThat(TEXTS).containsKey("event.step." + step));
        assertThat(ActivityKind.values()).allSatisfy(kind ->
                assertThat(TEXTS).containsKey("activity.kind." + kind));
    }

    /** The other way round: a key nobody asks for any more is dead weight. */
    @Test
    void noRuleTextIsLeftOver() {
        assertThat(TEXTS.stringPropertyNames())
                .filteredOn(key -> key.startsWith("rule."))
                .allSatisfy(key -> assertThat(Rule.values())
                        .anyMatch(rule -> key.equals("rule." + rule)));
    }

    private static Properties load() {
        Properties texts = new Properties();
        try (InputStream bundle = MessagesTest.class.getResourceAsStream("/messages.properties");
             Reader reader = new InputStreamReader(bundle, StandardCharsets.UTF_8)) {
            texts.load(reader);
        } catch (IOException e) {
            throw new IllegalStateException("MessagesTest :: the bundle could not be read", e);
        }
        return texts;
    }
}
