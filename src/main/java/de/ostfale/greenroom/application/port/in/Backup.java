package de.ostfale.greenroom.application.port.in;

import java.time.LocalDateTime;

/**
 * What the nightly backup last did. Read off the log the script leaves behind, never
 * stored — the same way the next step of an evening is read off the record.
 *
 * @param state how the last run ended
 * @param ranAt when it ran; null only when there is no log to read a time from
 */
public record Backup(State state, LocalDateTime ranAt) {

    /**
     * The four answers the log can give. A name, not a sentence: the German stands in
     * messages.properties under {@code backup.state.} and the template looks it up.
     */
    public enum State {

        /** A dump was written and pushed. The script says so only once the push is through. */
        PUSHED,

        /** Nothing had changed, so there was nothing to write. Also a good night. */
        UNCHANGED,

        /** The run broke off. What stands in the log is the error of whatever broke. */
        FAILED,

        /** No log: nothing has ever run here, or this is not the machine that runs it. */
        UNKNOWN
    }

    /** What there is to say where no log was found. */
    public static Backup unknown() {
        return new Backup(State.UNKNOWN, null);
    }
}
