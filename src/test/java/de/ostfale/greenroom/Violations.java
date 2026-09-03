package de.ostfale.greenroom;

import de.ostfale.greenroom.domain.Rule;
import de.ostfale.greenroom.domain.RuleViolated;

/**
 * What a test asserts about a refusal: the rule, not a piece of the message. Reading the
 * sentence back would be the coupling this whole mechanism was built to get rid of.
 */
public final class Violations {

    private Violations() {
    }

    /** The rule this code breaks — or a failure, if it breaks none. */
    public static Rule ruleBrokenBy(Runnable code) {
        try {
            code.run();
        } catch (RuleViolated e) {
            return e.rule();
        }
        throw new AssertionError("Violations :: expected a RuleViolated, but nothing was thrown");
    }
}
