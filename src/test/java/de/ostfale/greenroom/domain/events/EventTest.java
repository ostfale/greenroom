package de.ostfale.greenroom.domain.events;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static de.ostfale.greenroom.Fixtures.EVENING;
import static de.ostfale.greenroom.Fixtures.aReadyTalk;
import static de.ostfale.greenroom.Fixtures.aTalk;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Plain Java, no Spring: the evening carries its own rules. */
class EventTest {

    /** Any stored speaker will do here — the evening is what is under test. */
    private static final Long SPEAKER = 1L;

    private static Event published() {
        return Event.draftFor(aReadyTalk(SPEAKER))
                .withDate(EVENING)
                .moveTo(EventStatus.DATE_CONFIRMED)
                .withLocation(7L)
                .moveTo(EventStatus.VENUE_CONFIRMED)
                .moveTo(EventStatus.PUBLISHED);
    }

    // --- what an evening is at the start -------------------------------------------

    @Test
    void aTopicIsADraftWithoutADateOrAVenue() {
        Event event = Event.draftFor(aTalk(SPEAKER));

        assertThat(event.status()).isEqualTo(EventStatus.DRAFT);
        assertThat(event.date()).isNull();
        assertThat(event.locationId()).isNull();
        assertThat(event.mode()).isEqualTo(EventMode.ONSITE);
        assertThat(event.talks()).hasSize(1);
        assertThat(event.tags()).isEmpty();
    }

    @Test
    void anEveningNeedsAtLeastOneTalk() {
        assertThatThrownBy(() -> new Event(null, null, null, null, null, EventStatus.DRAFT, EventMode.ONSITE,
                null, List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("talk");

        assertThatThrownBy(() -> Event.draftFor(aReadyTalk(SPEAKER)).withTalks(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("talk");
    }

    // --- what the status promises, the record enforces ------------------------------

    @Test
    void aConfirmedDateCannotBeMissing() {
        Event topic = Event.draftFor(aReadyTalk(SPEAKER));

        assertThatThrownBy(() -> topic.moveTo(EventStatus.DATE_CONFIRMED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("date");
    }

    @Test
    void theDateCannotBeTakenAwayFromASettledEvening() {
        Event settled = Event.draftFor(aReadyTalk(SPEAKER)).withDate(EVENING).moveTo(EventStatus.DATE_CONFIRMED);

        assertThatThrownBy(() -> settled.withDate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("date");
    }

    @Test
    void aConfirmedVenueCannotBeMissing() {
        Event settled = Event.draftFor(aReadyTalk(SPEAKER)).withDate(EVENING).moveTo(EventStatus.DATE_CONFIRMED);

        assertThatThrownBy(() -> settled.moveTo(EventStatus.VENUE_CONFIRMED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("location");
    }

    @Test
    void anAnnouncedEveningNeedsATitleAndAnAbstractOnEveryTalk() {
        Event hosted = Event.draftFor(aReadyTalk(SPEAKER))
                .withDate(EVENING)
                .moveTo(EventStatus.DATE_CONFIRMED)
                .withLocation(7L)
                .moveTo(EventStatus.VENUE_CONFIRMED)
                .withAdditionalTalk(aTalk(2L).withTitle("Ohne Abstract"));

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
        Event topic = Event.draftFor(aReadyTalk(SPEAKER));

        assertThatThrownBy(() -> topic.moveTo(EventStatus.PUBLISHED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DRAFT");

        assertThatThrownBy(() -> published().moveTo(EventStatus.DONE).moveTo(EventStatus.PUBLISHED))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aTopicThatCameToNothingIsCancelledWithoutADate() {
        Event dropped = Event.draftFor(aTalk(SPEAKER)).moveTo(EventStatus.CANCELLED);

        assertThat(dropped.status()).isEqualTo(EventStatus.CANCELLED);
        assertThat(dropped.date()).isNull();
    }

    // --- the name of the evening ----------------------------------------------------

    @Test
    void theMottoNamesTheEveningWhenThereIsOne() {
        Event event = Event.draftFor(aReadyTalk(SPEAKER)).withMotto("Java-Herbst");

        assertThat(event.displayName()).isEqualTo("Java-Herbst");
    }

    @Test
    void withoutAMottoTheTalkNamesTheEvening() {
        assertThat(Event.draftFor(aReadyTalk(SPEAKER)).displayName()).isEqualTo("Records in Java 25");
    }

    @Test
    void aTopicWithoutATitleHasNoNameYet() {
        assertThat(Event.draftFor(aTalk(SPEAKER)).displayName()).isNull();
    }

    @Test
    void severalTalksMakeItASpecialDay() {
        Event event = Event.draftFor(aReadyTalk(SPEAKER));

        assertThat(event.hasSeveralTalks()).isFalse();
        assertThat(event.withAdditionalTalk(aTalk(2L)).hasSeveralTalks()).isTrue();
    }

    // --- tags -----------------------------------------------------------------------

    @Test
    void theKeywordsAreCopiedOntoTheEveningNotReferenced() {
        Event event = Event.draftFor(aReadyTalk(SPEAKER)).withTags(List.of("Java", "Records"));

        assertThat(event.tags()).containsExactly("Java", "Records");
        assertThat(event.carries("java")).isTrue();
        assertThat(event.carries("Testing")).isFalse();
    }

    @Test
    void theSameKeywordCannotBeOnTheEveningTwice() {
        Event event = Event.draftFor(aReadyTalk(SPEAKER));

        assertThatThrownBy(() -> event.withTags(List.of("Spring", "spring")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("twice");
    }

    @Test
    void aKeywordIsAWordOrItIsNotThere() {
        Event event = Event.draftFor(aReadyTalk(SPEAKER));

        assertThatThrownBy(() -> event.withTags(List.of("  ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("word");
        assertThat(event.withTags(List.of(" Java ")).tags()).containsExactly("Java");
    }

    @Test
    void tagsAreNeverSharedWithTheCaller() {
        List<String> mutable = new ArrayList<>(List.of("Java"));
        Event event = Event.draftFor(aReadyTalk(SPEAKER)).withTags(mutable);
        mutable.clear();

        assertThat(event.tags()).containsExactly("Java");
    }

    // --- the rest -------------------------------------------------------------------

    @Test
    void aBlankMottoIsNoMotto() {
        assertThat(Event.draftFor(aReadyTalk(SPEAKER)).withMotto("  ").motto()).isNull();
    }

    @Test
    void theModeIsKeptForTheImportedYears() {
        Event online = Event.draftFor(aReadyTalk(SPEAKER)).withMode(EventMode.ONLINE);

        assertThat(online.mode()).isEqualTo(EventMode.ONLINE);
    }

    // --- the talks of an evening -----------------------------------------------------

    @Test
    void aTalkIsChangedWhereItStandsAndKeepsItsSpeakers() {
        Event event = Event.draftFor(aTalk(SPEAKER)).withAdditionalTalk(aTalk(2L));

        Event changed = event.withTalkChanged(1,
                event.talkAt(1).withTitle("Records in Java 25"));

        assertThat(changed.talks()).extracting(Talk::title)
                .containsExactly(null, "Records in Java 25");
        assertThat(changed.talkAt(1).speakers()).extracting(TalkSpeaker::speakerId)
                .containsExactly(2L);
    }

    @Test
    void aTalkIsDroppedByItsPosition() {
        Event event = Event.draftFor(aReadyTalk(SPEAKER)).withAdditionalTalk(aTalk(2L));

        assertThat(event.withTalkRemoved(0).talks()).extracting(Talk::title)
                .containsExactly((String) null);
    }

    @Test
    void theLastTalkStays() {
        Event event = Event.draftFor(aReadyTalk(SPEAKER));

        assertThatThrownBy(() -> event.withTalkRemoved(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one talk");
    }

    @Test
    void thereIsNoTalkOutsideTheList() {
        Event event = Event.draftFor(aReadyTalk(SPEAKER));

        assertThatThrownBy(() -> event.talkAt(1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no talk at position");
        assertThatThrownBy(() -> event.withTalkRemoved(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no talk at position");
    }

    @Test
    void anAnnouncedEveningKeepsEveryTalkWorthAnnouncing() {
        Event published = published();

        assertThatThrownBy(() -> published.withTalkChanged(0, published.talkAt(0).withTitle(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("needs a title and an abstract");
    }

    // --- who leads through the evening ------------------------------------------------

    @Test
    void theModeratorIsNothingButAName() {
        Event event = Event.draftFor(aReadyTalk(SPEAKER)).withModerator("Max Muster");

        assertThat(event.moderator()).isEqualTo("Max Muster");
    }

    @Test
    void aBlankModeratorIsNoModerator() {
        assertThat(Event.draftFor(aReadyTalk(SPEAKER)).withModerator("  ").moderator()).isNull();
    }

    @Test
    void aTopicStartsWithoutOne() {
        assertThat(Event.draftFor(aReadyTalk(SPEAKER)).moderator()).isNull();
    }

    @Test
    void theNotesAreFreeTextAndBlankIsNone() {
        Event event = Event.draftFor(aReadyTalk(SPEAKER));

        assertThat(event.notes()).isNull();
        assertThat(event.withNotes("   ").notes()).isNull();
        assertThat(event.withNotes("Beamer mitbringen.").notes()).isEqualTo("Beamer mitbringen.");
    }

    @Test
    void anEveningWithoutAMottoBorrowsTheNameOfItsFirstTalk() {
        Event event = Event.draftFor(aReadyTalk(SPEAKER)).withAdditionalTalk(aTalk(2L));

        assertThat(event.nameFromItsTalk()).isEqualTo("Records in Java 25");
        assertThat(event.displayName()).isEqualTo("Records in Java 25");
    }

    /** Borrowed, not copied: renaming the talk renames the evening with it. */
    @Test
    void theBorrowedNameFollowsTheTalk() {
        Event event = Event.draftFor(aReadyTalk(SPEAKER));

        Event renamed = event.withTalkChanged(0, event.talkAt(0).withTitle("Virtual Threads"));

        assertThat(renamed.motto()).isNull();
        assertThat(renamed.displayName()).isEqualTo("Virtual Threads");
    }

    @Test
    void aMottoOfItsOwnWins() {
        Event event = Event.draftFor(aReadyTalk(SPEAKER)).withMotto("Java-Herbst");

        assertThat(event.displayName()).isEqualTo("Java-Herbst");
        assertThat(event.nameFromItsTalk()).isEqualTo("Records in Java 25");
    }
}
