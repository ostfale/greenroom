package de.ostfale.greenroom.domain.events;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
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
     * The steps allowed from here — the same rule as {@link #canMoveTo}, read as a list so
     * a page can offer exactly those and nothing else. The one that carries the planning
     * forward comes first and the ones that put it aside or take it back after it; within
     * each group the states stay in the order they are declared. A page offers them in
     * that order, so the step somebody usually wants is the first one they see. Empty once
     * the evening is closed.
     */
    public List<EventStatus> allowedTargets() {
        return Arrays.stream(values())
                .filter(this::canMoveTo)
                .sorted(Comparator.comparing(target -> !carriesOnTo(target)))
                .toList();
    }

    /**
     * The way an evening goes, as against the two states it can be put into from anywhere.
     * Postponed and cancelled are not further along than anything and not behind it either
     * — they are beside the track, which is why they count no step.
     */
    public boolean isOnTheTrack() {
        return this != POSTPONED && this != CANCELLED;
    }

    /**
     * True where the step to {@code target} carries the planning on, as against putting
     * the evening aside or taking it back. The page offers this one first and in the
     * strong colour: it is the step somebody usually means.
     */
    public boolean carriesOnTo(EventStatus target) {
        return canMoveTo(target) && target.isOnTheTrack() && target.plannedSteps() > plannedSteps();
    }

    /**
     * True where this state was already behind {@code other} — a step to it undoes a
     * confirmation, and a page has to say so rather than offer it like the next one.
     */
    public boolean isBehind(EventStatus other) {
        return isOnTheTrack() && other.isOnTheTrack() && plannedSteps() < other.plannedSteps();
    }

    /**
     * The four confirmations an evening collects, in the order it collects them. The
     * counterpart to {@link #plannedSteps()}: that one counts how many are behind us, this
     * one says what they were, so a page can name the steps instead of drawing four
     * anonymous bars.
     */
    public static List<EventStatus> milestones() {
        return List.of(DATE_CONFIRMED, VENUE_CONFIRMED, PUBLISHED, DONE);
    }

    /**
     * Nothing left to do. Postponed is explicitly not closed.
     */
    public boolean isClosed() {
        return this == DONE || this == CANCELLED;
    }

    /**
     * How much of the planning is behind us, counted in the four steps an evening takes:
     * topic, date, venue, announcement. Postponed and cancelled are not steps on that way,
     * so they count nothing.
     */
    public int plannedSteps() {
        return switch (this) {
            case DRAFT -> 0;
            case DATE_CONFIRMED -> 1;
            case VENUE_CONFIRMED -> 2;
            case PUBLISHED -> 3;
            case DONE -> 4;
            case POSTPONED, CANCELLED -> 0;
        };
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
