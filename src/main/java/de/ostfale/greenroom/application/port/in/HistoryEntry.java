package de.ostfale.greenroom.application.port.in;

import de.ostfale.greenroom.domain.activities.Activity;
import de.ostfale.greenroom.domain.activities.ActivityDirection;
import de.ostfale.greenroom.domain.activities.InquiryOutcome;

import java.time.LocalDate;

/**
 * One line of what happened to an evening. Not an aggregate and nothing that is stored:
 * the entries come from three places — the inquiries to the speakers, the inquiries to the
 * places, and the {@link Activity} entries written by hand — and are put in one order the
 * moment the page asks for them.
 *
 * <p>Mixed at read time on purpose. Writing an inquiry into the log as well would keep the
 * same fact in two tables, and the second copy would be the one that goes stale.
 *
 * <p>The fields carry what happened, never how it reads: the German belongs to the
 * template. {@code note} is set for a hand-written entry, {@code outcome} for an answer,
 * {@code about} for a question that named a date — never more than one of them.
 */
public record HistoryEntry(
        LocalDate on,
        ActivityDirection direction,
        String who,
        String note,
        LocalDate about,
        InquiryOutcome outcome) {

    /** A question that went out to somebody, naming the date it was about. */
    public static HistoryEntry asked(LocalDate on, String who, LocalDate about) {
        return new HistoryEntry(on, ActivityDirection.OUTGOING, who, null, about, null);
    }

    /** The word that came back. */
    public static HistoryEntry answered(LocalDate on, String who, InquiryOutcome outcome) {
        return new HistoryEntry(on, ActivityDirection.INCOMING, who, null, null, outcome);
    }

    /** A line somebody wrote by hand, in whatever direction it went. */
    public static HistoryEntry from(Activity activity) {
        return new HistoryEntry(activity.happenedOn(), activity.direction(), null,
                activity.what(), null, null);
    }
}
