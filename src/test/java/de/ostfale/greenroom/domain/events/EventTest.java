package de.ostfale.greenroom.domain.events;

import de.ostfale.greenroom.domain.Rule;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
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

    /**
     * The canonical constructor is what a stored row comes back through, so it refuses by
     * name what the factories can never hand it.
     */
    @Test
    void anEveningNeedsAStatusAModeAndATalk() {
        assertThat(ruleBrokenBy(() -> new Event(1L, null, null, null, null,
                null, EventMode.ONSITE, null, null, List.of(aReadyTalk(SPEAKER)))))
                .isEqualTo(Rule.EVENT_NEEDS_A_STATUS);
        assertThat(ruleBrokenBy(() -> new Event(1L, null, null, null, null,
                EventStatus.DRAFT, null, null, null, List.of(aReadyTalk(SPEAKER)))))
                .isEqualTo(Rule.EVENT_NEEDS_A_MODE);
        assertThat(ruleBrokenBy(() -> new Event(1L, null, null, null, null,
                EventStatus.DRAFT, EventMode.ONSITE, null, null, null)))
                .isEqualTo(Rule.EVENT_NEEDS_ONE_TALK);
    }

    /** A filter standing on "alle" asks about nobody, and nobody speaks nowhere. */
    @Test
    void nobodyIsNoSpeakerAndNowhereIsNoPlace() {
        Event evening = published();

        assertThat(evening.isGivenBy(null)).isFalse();
        assertThat(evening.isAt(null)).isFalse();
    }

    @Test
    void anEveningNeedsAtLeastOneTalk() {
        assertThat(ruleBrokenBy(() -> new Event(null, null, null, null, null, EventStatus.DRAFT, EventMode.ONSITE,
                null, null, List.of())))
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

    // --- what the evening says about itself ---------------------------------------------

    @Test
    void theWordsAreLookedForWhereverTheEveningKeepsThem() {
        Event evening = Event.draftFor(aReadyTalk(SPEAKER).withTags(List.of("Architektur")))
                .withMotto("Java-Herbst")
                .withNotes("Beamer vom Ort geliehen");

        assertThat(evening.mentions("java-herbst")).isTrue();
        assertThat(evening.mentions("Records in Java 25")).isTrue();
        assertThat(evening.mentions("weniger Tippen")).isTrue();
        assertThat(evening.mentions("beamer")).isTrue();
        assertThat(evening.mentions("architektur")).isTrue();
        assertThat(evening.mentions("arc42")).isFalse();
    }

    /** Where somebody worked back then stands in the announced biography, and is searched. */
    @Test
    void theBiographyTheEveningAnnouncedIsSearchedToo() {
        Event evening = Event.draftFor(aTalk(SPEAKER).withSpeakers(
                List.of(TalkSpeaker.of(SPEAKER).withAnnouncedBio("Architekt bei Hapag-Lloyd"))));

        assertThat(evening.mentions("hapag")).isTrue();
    }

    @Test
    void caseIsIgnoredAndAskingNothingMatchesEverything() {
        Event evening = Event.draftFor(aReadyTalk(SPEAKER)).withMotto("Java-Herbst");

        assertThat(evening.mentions("JAVA-herbst")).isTrue();
        assertThat(evening.mentions("  ")).isTrue();
        assertThat(evening.mentions(null)).isTrue();
    }

    // --- which address the evening was at -----------------------------------------------

    @Test
    void theEveningCanNameWhichAddressItWasAt() {
        Event evening = Event.draftFor(aReadyTalk(SPEAKER)).withLocation(VENUE);

        assertThat(evening.addressPosition()).isNull();
        assertThat(evening.withAddressAt(1).addressPosition()).isEqualTo(1);
    }

    /** A position points into one place's list; at another place the number means a house. */
    @Test
    void thePinnedAddressDoesNotTravelToAnotherPlace() {
        Event evening = Event.draftFor(aReadyTalk(SPEAKER)).withLocation(VENUE).withAddressAt(1);

        assertThat(evening.withLocation(VENUE).addressPosition()).isEqualTo(1);
        assertThat(evening.withLocation(VENUE + 1).addressPosition()).isNull();
        assertThat(evening.withLocation(null).addressPosition()).isNull();
    }

    @Test
    void thePinSurvivesEverythingElseTheEveningDoes() {
        Event evening = Event.draftFor(aReadyTalk(SPEAKER))
                .withLocation(VENUE).withAddressAt(1).withDate(EVENING);

        assertThat(evening.moveTo(EventStatus.DATE_CONFIRMED).addressPosition()).isEqualTo(1);
        assertThat(evening.withMotto("Java-Herbst").addressPosition()).isEqualTo(1);
    }

    // --- when the evening begins --------------------------------------------------------

    /**
     * The time sits on the talk, so the evening has none of its own: it begins when the
     * first of them does. With several talks that is the whole point of putting it there.
     */
    @Test
    void anEveningBeginsWithTheEarliestOfItsTalks() {
        Event evening = Event.draftFor(aReadyTalk(SPEAKER).withStartsAt(LocalTime.of(20, 0)))
                .withAdditionalTalk(aReadyTalk(SPEAKER + 1).withStartsAt(LocalTime.of(19, 0)));

        assertThat(evening.startsAt()).isEqualTo(LocalTime.of(19, 0));
    }

    @Test
    void anEveningNobodyNotedAnHourForBeginsAtNoStatedTime() {
        Event evening = Event.draftFor(aReadyTalk(SPEAKER).withStartsAt(null));

        assertThat(evening.startsAt()).isNull();
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

    /** The evening has no list of its own: it reads the words off the talks it carries. */
    @Test
    void theKeywordsOfTheEveningAreTheKeywordsOfItsTalks() {
        Event event = Event.draftFor(aReadyTalk(SPEAKER).withTags(List.of("Java", "Records")))
                .withAdditionalTalk(aTalk(2L).withTags(List.of("Spring")));

        assertThat(event.tags()).containsExactly("Java", "Records", "Spring");
        assertThat(event.carries("java")).isTrue();
        assertThat(event.carries("Testing")).isFalse();
    }

    /** Two talks about the same thing name it once, however it was capitalised. */
    @Test
    void aWordOnTwoTalksIsOneWordOnTheEvening() {
        Event event = Event.draftFor(aReadyTalk(SPEAKER).withTags(List.of("Spring")))
                .withAdditionalTalk(aTalk(2L).withTags(List.of("spring", "Testing")));

        assertThat(event.tags()).containsExactly("Spring", "Testing");
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
