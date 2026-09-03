package de.ostfale.greenroom.domain.events;

import de.ostfale.greenroom.domain.Rule;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static de.ostfale.greenroom.Fixtures.EVENING;
import static de.ostfale.greenroom.Fixtures.aReadyTalk;
import static de.ostfale.greenroom.Fixtures.aTalk;
import static de.ostfale.greenroom.Violations.ruleBrokenBy;
import static org.assertj.core.api.Assertions.assertThat;

/** Plain Java, no Spring: the evening carries its own rules. */
class EventTest {

    /** Any stored speaker and any stored place will do — the evening is under test. */
    private static final Long SPEAKER = 1L;
    private static final Long VENUE = 7L;

    private static Event published() {
        return Event.draftFor(aReadyTalk(SPEAKER))
                .withDate(EVENING)
                .moveTo(EventStatus.DATE_CONFIRMED)
                .withLocation(VENUE)
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
        assertThat(ruleBrokenBy(() -> new Event(null, null, null, null, null, EventStatus.DRAFT, EventMode.ONSITE,
                null, List.of(), List.of())))
                .isEqualTo(Rule.EVENT_NEEDS_ONE_TALK);

        assertThat(ruleBrokenBy(() -> Event.draftFor(aReadyTalk(SPEAKER)).withTalks(List.of())))
                .isEqualTo(Rule.EVENT_NEEDS_ONE_TALK);
    }

    // --- what the status promises, the record enforces ------------------------------

    @Test
    void aConfirmedDateCannotBeMissing() {
        Event topic = Event.draftFor(aReadyTalk(SPEAKER));

        assertThat(ruleBrokenBy(() -> topic.moveTo(EventStatus.DATE_CONFIRMED)))
                .isEqualTo(Rule.EVENT_NEEDS_A_DATE);
    }

    @Test
    void theDateCannotBeTakenAwayFromASettledEvening() {
        Event settled = Event.draftFor(aReadyTalk(SPEAKER)).withDate(EVENING).moveTo(EventStatus.DATE_CONFIRMED);

        assertThat(ruleBrokenBy(() -> settled.withDate(null)))
                .isEqualTo(Rule.EVENT_NEEDS_A_DATE);
    }

    @Test
    void aConfirmedVenueCannotBeMissing() {
        Event settled = Event.draftFor(aReadyTalk(SPEAKER)).withDate(EVENING).moveTo(EventStatus.DATE_CONFIRMED);

        assertThat(ruleBrokenBy(() -> settled.moveTo(EventStatus.VENUE_CONFIRMED)))
                .isEqualTo(Rule.EVENT_NEEDS_A_LOCATION);
    }

    @Test
    void anAnnouncedEveningNeedsATitleAndAnAbstractOnEveryTalk() {
        Event hosted = Event.draftFor(aReadyTalk(SPEAKER))
                .withDate(EVENING)
                .moveTo(EventStatus.DATE_CONFIRMED)
                .withLocation(VENUE)
                .moveTo(EventStatus.VENUE_CONFIRMED)
                .withAdditionalTalk(aTalk(2L).withTitle("Ohne Abstract"));

        assertThat(hosted.allTalksAreReadyToPublish()).isFalse();
        assertThat(ruleBrokenBy(() -> hosted.moveTo(EventStatus.PUBLISHED)))
                .isEqualTo(Rule.EVENT_NEEDS_PUBLISHABLE_TALKS);
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

        assertThat(ruleBrokenBy(() -> topic.moveTo(EventStatus.PUBLISHED)))
                .isEqualTo(Rule.EVENT_DOES_NOT_MOVE);

        assertThat(ruleBrokenBy(() -> published().moveTo(EventStatus.DONE).moveTo(EventStatus.PUBLISHED)))
                .isEqualTo(Rule.EVENT_DOES_NOT_MOVE);
    }

    @Test
    void aTopicThatCameToNothingIsCancelledWithoutADate() {
        Event dropped = Event.draftFor(aTalk(SPEAKER)).moveTo(EventStatus.CANCELLED);

        assertThat(dropped.status()).isEqualTo(EventStatus.CANCELLED);
        assertThat(dropped.date()).isNull();
    }

    // --- what an evening is waiting for -----------------------------------------------

    @Test
    void aTopicWaitsForADateAndThenForAVenue() {
        Event topic = Event.draftFor(aReadyTalk(SPEAKER));

        assertThat(topic.nextStep(EVENING)).isEqualTo(NextStep.FIND_A_DATE);
        assertThat(topic.withDate(EVENING).nextStep(EVENING)).isEqualTo(NextStep.FIND_A_VENUE);
    }

    @Test
    void anEveningWithAVenueWaitsForWhatTheTalksStillOwe() {
        Event hosted = Event.draftFor(aTalk(SPEAKER)).withDate(EVENING).withLocation(VENUE);

        assertThat(hosted.nextStep(EVENING)).isEqualTo(NextStep.WRITE_THE_ABSTRACT);
        assertThat(hosted.withTalks(List.of(aReadyTalk(SPEAKER))).nextStep(EVENING))
                .isEqualTo(NextStep.ANNOUNCE_IT);
    }

    /** Announced and the day is gone: somebody has to say that it happened. */
    @Test
    void anAnnouncedEveningWaitsForItsDayAndThenToBeClosed() {
        Event announced = published();

        assertThat(announced.nextStep(EVENING.minusDays(1))).isEqualTo(NextStep.NOTHING);
        assertThat(announced.nextStep(EVENING.plusDays(1))).isEqualTo(NextStep.CLOSE_IT);
    }

    @Test
    void aClosedEveningWaitsForNothingAndAPostponedOneForANewDate() {
        assertThat(published().moveTo(EventStatus.DONE).nextStep(EVENING))
                .isEqualTo(NextStep.NOTHING);
        assertThat(published().moveTo(EventStatus.POSTPONED).nextStep(EVENING))
                .isEqualTo(NextStep.FIND_A_DATE);
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

        assertThat(ruleBrokenBy(() -> event.withTags(List.of("Spring", "spring"))))
                .isEqualTo(Rule.TAG_TWICE_ON_EVENT);
    }

    @Test
    void aKeywordIsAWordOrItIsNotThere() {
        Event event = Event.draftFor(aReadyTalk(SPEAKER));

        assertThat(ruleBrokenBy(() -> event.withTags(List.of("  "))))
                .isEqualTo(Rule.TAG_NEEDS_A_WORD);
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

        assertThat(ruleBrokenBy(() -> event.withTalkRemoved(0)))
                .isEqualTo(Rule.EVENT_NEEDS_ONE_TALK);
    }

    @Test
    void thereIsNoTalkOutsideTheList() {
        Event event = Event.draftFor(aReadyTalk(SPEAKER));

        assertThat(ruleBrokenBy(() -> event.talkAt(1)))
                .isEqualTo(Rule.NO_TALK_AT_POSITION);
        assertThat(ruleBrokenBy(() -> event.withTalkRemoved(-1)))
                .isEqualTo(Rule.NO_TALK_AT_POSITION);
    }

    @Test
    void anAnnouncedEveningKeepsEveryTalkWorthAnnouncing() {
        Event published = published();

        assertThat(ruleBrokenBy(() -> published.withTalkChanged(0, published.talkAt(0).withTitle(null))))
                .isEqualTo(Rule.EVENT_NEEDS_PUBLISHABLE_TALKS);
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
