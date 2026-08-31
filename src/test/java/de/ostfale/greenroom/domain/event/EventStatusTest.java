package de.ostfale.greenroom.domain.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static de.ostfale.greenroom.domain.event.EventStatus.CANCELLED;
import static de.ostfale.greenroom.domain.event.EventStatus.DATE_CONFIRMED;
import static de.ostfale.greenroom.domain.event.EventStatus.DONE;
import static de.ostfale.greenroom.domain.event.EventStatus.DRAFT;
import static de.ostfale.greenroom.domain.event.EventStatus.POSTPONED;
import static de.ostfale.greenroom.domain.event.EventStatus.PUBLISHED;
import static de.ostfale.greenroom.domain.event.EventStatus.VENUE_CONFIRMED;
import static org.assertj.core.api.Assertions.assertThat;

/** The state machine of an evening, pinned down step by step. */
class EventStatusTest {

    @Test
    void aTopicBecomesAnEveningOrComesToNothing() {
        assertThat(allowedFrom(DRAFT)).containsExactlyInAnyOrder(DATE_CONFIRMED, CANCELLED);
    }

    @Test
    void aDateIsFollowedByAVenue() {
        assertThat(allowedFrom(DATE_CONFIRMED))
                .containsExactlyInAnyOrder(VENUE_CONFIRMED, POSTPONED, CANCELLED);
    }

    @Test
    void aConfirmedVenueCanFallThroughAgain() {
        assertThat(allowedFrom(VENUE_CONFIRMED))
                .containsExactlyInAnyOrder(PUBLISHED, DATE_CONFIRMED, POSTPONED, CANCELLED);
    }

    @Test
    void anAnnouncedEveningIsHeldPostponedOrCalledOff() {
        assertThat(allowedFrom(PUBLISHED)).containsExactlyInAnyOrder(DONE, POSTPONED, CANCELLED);
    }

    @Test
    void aPostponedEveningGoesBackToBeingPlanned() {
        assertThat(allowedFrom(POSTPONED)).containsExactlyInAnyOrder(DRAFT, DATE_CONFIRMED, CANCELLED);
    }

    @ParameterizedTest
    @EnumSource(names = {"DONE", "CANCELLED"})
    void whatIsClosedStaysClosed(EventStatus closed) {
        assertThat(allowedFrom(closed)).isEmpty();
        assertThat(closed.isClosed()).isTrue();
    }

    @Test
    void postponedIsExplicitlyNotClosed() {
        assertThat(POSTPONED.isClosed()).isFalse();
        assertThat(DRAFT.isClosed()).isFalse();
    }

    @ParameterizedTest
    @EnumSource
    void noStatusMovesToItself(EventStatus status) {
        assertThat(status.canMoveTo(status)).isFalse();
    }

    @Test
    void onlyASettledEveningNeedsADate() {
        assertThat(statusesThat(EventStatus::requiresADate))
                .containsExactlyInAnyOrder(DATE_CONFIRMED, VENUE_CONFIRMED, PUBLISHED, DONE);
    }

    @Test
    void onlyAHostedEveningNeedsAVenue() {
        assertThat(statusesThat(EventStatus::requiresAVenue))
                .containsExactlyInAnyOrder(VENUE_CONFIRMED, PUBLISHED, DONE);
    }

    @Test
    void onlyAnAnnouncedEveningNeedsTitlesAndAbstracts() {
        assertThat(statusesThat(EventStatus::requiresPublishableTalks))
                .containsExactlyInAnyOrder(PUBLISHED, DONE);
    }

    private static Set<EventStatus> allowedFrom(EventStatus status) {
        return Arrays.stream(EventStatus.values())
                .filter(status::canMoveTo)
                .collect(Collectors.toSet());
    }

    private static Set<EventStatus> statusesThat(Predicate<EventStatus> rule) {
        return Arrays.stream(EventStatus.values())
                .filter(rule)
                .collect(Collectors.toSet());
    }
}
