package de.ostfale.greenroom.domain.activities;

/**
 * The two things that happen to an evening and are worth a line: a mail went out, or one
 * came back. Nothing else — a thought that is not an event of the evening is a
 * {@link de.ostfale.greenroom.domain.notes.Note}, and what a field already holds is not
 * written here a second time.
 */
public enum ActivityKind {

    MAIL_SENT,
    MAIL_RECEIVED
}
