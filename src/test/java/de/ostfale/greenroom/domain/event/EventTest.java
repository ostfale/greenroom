package de.ostfale.greenroom.domain.event;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Plain Java, no Spring: the evening carries its own rules. */
class EventTest {

    private static final LocalDate EVENING = LocalDate.of(2026, 9, 24);

    private static Talk readyTalk() {
        return Talk.by(TalkSpeaker.of(1L))
                .withTitle("Records in Java 25")
                .withAbstract("Warum Records mehr sind als weniger Tippen.");
    }

    private static Event published() {
        return Event.draftFor(readyTalk())
                .withDate(EVENING)
                .moveTo(EventStatus.DATE_CONFIRMED)
                .withLocation(7L)
                .moveTo(EventStatus.VENUE_CONFIRMED)
                .moveTo(EventStatus.PUBLISHED);
    }

    // --- what an evening is at the start -------------------------------------------

    @Test
    void aTopicIsADraftWithoutADateOrAVenue() {
        Event event = Event.draftFor(Talk.by(TalkSpeaker.of(1L)));

        assertThat(event.status()).isEqualTo(EventStatus.DRAFT);
        assertThat(event.date()).isNull();
        assertThat(event.locationId()).isNull();
        assertThat(event.mode()).isEqualTo(EventMode.ONSITE);
        assertThat(event.talks()).hasSize(1);
        assertThat(event.tags()).isEmpty();
    }

    @Test
    void anEveningNeedsAtLeastOneTalk() {
        assertThatThrownBy(() -> new Event(null, null, null, EventStatus.DRAFT, EventMode.ONSITE,
                null, List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("talk");

        assertThatThrownBy(() -> Event.draftFor(readyTalk()).withTalks(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("talk");
    }

    // --- what the status promises, the record enforces ------------------------------

    @Test
    void aConfirmedDateCannotBeMissing() {
        Event topic = Event.draftFor(readyTalk());

        assertThatThrownBy(() -> topic.moveTo(EventStatus.DATE_CONFIRMED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("date");
    }

    @Test
    void theDateCannotBeTakenAwayFromASettledEvening() {
        Event settled = Event.draftFor(readyTalk()).withDate(EVENING).moveTo(EventStatus.DATE_CONFIRMED);

        assertThatThrownBy(() -> settled.withDate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("date");
    }

    @Test
    void aConfirmedVenueCannotBeMissing() {
        Event settled = Event.draftFor(readyTalk()).withDate(EVENING).moveTo(EventStatus.DATE_CONFIRMED);

        assertThatThrownBy(() -> settled.moveTo(EventStatus.VENUE_CONFIRMED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("location");
    }

    @Test
    void anAnnouncedEveningNeedsATitleAndAnAbstractOnEveryTalk() {
        Event hosted = Event.draftFor(readyTalk())
                .withDate(EVENING)
                .moveTo(EventStatus.DATE_CONFIRMED)
                .withLocation(7L)
                .moveTo(EventStatus.VENUE_CONFIRMED)
                .withAdditionalTalk(Talk.by(TalkSpeaker.of(2L)).withTitle("Ohne Abstract"));

        assertThat(hosted.allTalksAreReadyToPublish()).isFalse();
        assertThatThrownBy(() -> hosted.moveTo(EventStatus.PUBLISHED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("abstract");
    }

    @Test
    void anEveningWalksAllTheWayToDone() {
        Event done = published().moveTo(EventStatus.DONE);

        assertThat(done.status()).isEqualTo(EventStatus.DONE);
        assertThat(done.status().isClosed()).isTrue();
    }

    @Test
    void aStepTheStateMachineForbidsIsRefused() {
        Event topic = Event.draftFor(readyTalk());

        assertThatThrownBy(() -> topic.moveTo(EventStatus.PUBLISHED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DRAFT");

        assertThatThrownBy(() -> published().moveTo(EventStatus.DONE).moveTo(EventStatus.PUBLISHED))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aTopicThatCameToNothingIsCancelledWithoutADate() {
        Event dropped = Event.draftFor(Talk.by(TalkSpeaker.of(1L))).moveTo(EventStatus.CANCELLED);

        assertThat(dropped.status()).isEqualTo(EventStatus.CANCELLED);
        assertThat(dropped.date()).isNull();
    }

    // --- the name of the evening ----------------------------------------------------

    @Test
    void theMottoNamesTheEveningWhenThereIsOne() {
        Event event = Event.draftFor(readyTalk()).withMotto("Java-Herbst");

        assertThat(event.displayName()).isEqualTo("Java-Herbst");
    }

    @Test
    void withoutAMottoTheTalkNamesTheEvening() {
        assertThat(Event.draftFor(readyTalk()).displayName()).isEqualTo("Records in Java 25");
    }

    @Test
    void aTopicWithoutATitleHasNoNameYet() {
        assertThat(Event.draftFor(Talk.by(TalkSpeaker.of(1L))).displayName()).isNull();
    }

    @Test
    void severalTalksMakeItASpecialDay() {
        Event event = Event.draftFor(readyTalk());

        assertThat(event.hasSeveralTalks()).isFalse();
        assertThat(event.withAdditionalTalk(Talk.by(TalkSpeaker.of(2L))).hasSeveralTalks()).isTrue();
    }

    // --- tags -----------------------------------------------------------------------

    @Test
    void theSameTagCannotBeOnTheEveningTwice() {
        Event event = Event.draftFor(readyTalk());

        assertThatThrownBy(() -> event.withTags(List.of(EventTag.of(1L), EventTag.of(1L))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("twice");
    }

    @Test
    void theEveningKnowsWhichTagsItCarries() {
        Event event = Event.draftFor(readyTalk()).withTags(List.of(EventTag.of(1L), EventTag.of(2L)));

        assertThat(event.carries(1L)).isTrue();
        assertThat(event.carries(3L)).isFalse();
        assertThat(event.tags()).extracting(EventTag::tagId).containsExactly(1L, 2L);
    }

    @Test
    void aTagOnAnEveningAlwaysPointsAtAStoredTag() {
        assertThatThrownBy(() -> EventTag.of(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tag");
    }

    // --- the rest -------------------------------------------------------------------

    @Test
    void aBlankMottoIsNoMotto() {
        assertThat(Event.draftFor(readyTalk()).withMotto("  ").motto()).isNull();
    }

    @Test
    void theModeIsKeptForTheImportedYears() {
        Event online = Event.draftFor(readyTalk()).withMode(EventMode.ONLINE);

        assertThat(online.mode()).isEqualTo(EventMode.ONLINE);
    }
}
