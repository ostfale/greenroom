package de.ostfale.greenroom.domain;

import java.util.Arrays;

/**
 * Something was refused, and this says which rule refused it. The only exception the
 * records throw: what is missing is a {@link Rule}, not a sentence, so nothing has to read
 * a message back to find out what happened.
 *
 * <p>{@code args} are the values that made the refusal concrete — the position that was
 * out of range, the tag that was already there. They go into the log and are available to
 * the German text, which so far does not use them: a sentence that names no number reads
 * better on the page than one that does.
 */
public final class RuleViolated extends RuntimeException {

    private final Rule rule;
    private final transient Object[] args;

    public RuleViolated(Rule rule, Object... args) {
        super(args.length == 0 ? rule.name() : rule.name() + " " + Arrays.toString(args));
        this.rule = rule;
        this.args = args;
    }

    public Rule rule() {
        return rule;
    }

    /** A copy — an exception hands out nothing that could be changed under it. */
    public Object[] args() {
        return args.clone();
    }
}
