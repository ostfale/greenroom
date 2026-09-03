package de.ostfale.greenroom.domain;

/**
 * Everything that may be refused, by name. A record that will not accept something names
 * the rule it is standing on instead of writing a sentence about it — the sentence is
 * German, it belongs on the page, and it lives in {@code messages.properties} under
 * {@code rule.<NAME>}.
 *
 * <p>Most of these come from the domain. A few come from the edge, where a form sends
 * nothing where something was expected or a date that is not one: they are refusals the
 * page has to explain in the same way, so they are the same kind of thing and share the
 * mechanism rather than getting a second one.
 *
 * <p>Renaming a constant is safe — the compiler finds every use. Removing one without
 * removing its key, or adding one without adding a key, is what {@code MessagesTest}
 * catches.
 */
public enum Rule {

    /** What an evening is, and what its status promises. */
    EVENT_NEEDS_A_STATUS,
    EVENT_NEEDS_A_MODE,
    EVENT_NEEDS_ONE_TALK,
    EVENT_NEEDS_A_DATE,
    EVENT_NEEDS_A_LOCATION,
    EVENT_NEEDS_PUBLISHABLE_TALKS,
    EVENT_DOES_NOT_MOVE,
    NO_TALK_AT_POSITION,
    TAG_NEEDS_A_WORD,
    TAG_TWICE_ON_EVENT,

    /** A talk and the people who give it. */
    TALK_NEEDS_A_SPEAKER,
    SPEAKER_TWICE_ON_TALK,
    SPEAKER_NOT_STORED,

    /** What was asked, and what came back. */
    INQUIRY_BELONGS_TO_AN_EVENT,
    INQUIRY_NEEDS_A_SPEAKER,
    INQUIRY_NEEDS_A_LOCATION,
    INQUIRY_NEEDS_A_SENT_DATE,
    INQUIRY_NEEDS_A_CHANNEL,
    INQUIRY_NEEDS_AN_OUTCOME,
    INQUIRY_ANSWER_IS_DATED,
    INQUIRY_ALREADY_ANSWERED,
    PENDING_IS_NOT_AN_ANSWER,
    VENUE_INQUIRY_NEEDS_A_DATE,
    NO_SUCH_INQUIRY,

    /** A line in the history. */
    ACTIVITY_BELONGS_TO_AN_EVENT,
    ACTIVITY_IS_DATED,
    ACTIVITY_NEEDS_A_DIRECTION,
    ACTIVITY_NEEDS_A_CHANNEL,
    ACTIVITY_NEEDS_A_TEXT,
    NOTE_HAS_NO_CHANNEL,

    /** A mail that is not worth sending. */
    MAIL_NEEDS_A_RECIPIENT,
    MAIL_NEEDS_A_SUBJECT,
    MAIL_NEEDS_A_BODY,

    /** What the form left empty, sent unreadably, or pointed at and did not find. */
    NO_SPEAKER_CHOSEN,
    NO_LOCATION_CHOSEN,
    NO_CONTACT_CHOSEN,
    DATE_UNREADABLE,
    NOT_FOUND
}
