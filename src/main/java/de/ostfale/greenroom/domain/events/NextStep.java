package de.ostfale.greenroom.domain.events;

/**
 * The one thing an evening is waiting for. Not a second state machine beside
 * {@link EventStatus}: the status says how far the planning has come, this says what the
 * next hand has to do, and the overview is the only place that asks.
 */
public enum NextStep {

    FIND_A_DATE,
    FIND_A_VENUE,
    WRITE_THE_ABSTRACT,
    ANNOUNCE_IT,
    CLOSE_IT,
    NOTHING
}
