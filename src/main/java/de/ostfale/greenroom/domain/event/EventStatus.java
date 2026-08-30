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
}
