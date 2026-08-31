package de.ostfale.greenroom.domain.event;

import java.util.Set;

public enum EventStatus {

    DRAFT,
    DATE_CONFIRMED,
    VENUE_CONFIRMED,
    PUBLISHED,
    POSTPONED,
    DONE,
    CANCELLED;

    public boolean canMoveTo(EventStatus target) {
        return switch (this) {
            case DRAFT -> Set.of(DATE_CONFIRMED, CANCELLED).contains(target);
            case DATE_CONFIRMED -> Set.of(VENUE_CONFIRMED, POSTPONED, CANCELLED).contains(target);
            case VENUE_CONFIRMED -> Set.of(PUBLISHED, DATE_CONFIRMED, POSTPONED, CANCELLED).contains(target);
            case PUBLISHED -> Set.of(DONE, POSTPONED, CANCELLED).contains(target);
            case POSTPONED -> Set.of(DRAFT, DATE_CONFIRMED, CANCELLED).contains(target);
            case DONE, CANCELLED -> false;
        };
    }

    /**
     * Nothing left to do. Postponed is explicitly not closed.
     */
    public boolean isClosed() {
        return this == DONE || this == CANCELLED;
    }

    /** From here on the evening has a date. A draft is a topic, and a topic has no date. */
    public boolean requiresADate() {
        return switch (this) {
            case DATE_CONFIRMED, VENUE_CONFIRMED, PUBLISHED, DONE -> true;
            case DRAFT, POSTPONED, CANCELLED -> false;
        };
    }

    /** From here on somebody has said yes to hosting it. */
    public boolean requiresAVenue() {
        return switch (this) {
            case VENUE_CONFIRMED, PUBLISHED, DONE -> true;
            case DRAFT, DATE_CONFIRMED, POSTPONED, CANCELLED -> false;
        };
    }

    /** From here on the announcement is out, so every talk carries a title and an abstract. */
    public boolean requiresPublishableTalks() {
        return switch (this) {
            case PUBLISHED, DONE -> true;
            case DRAFT, DATE_CONFIRMED, VENUE_CONFIRMED, POSTPONED, CANCELLED -> false;
        };
    }
}
