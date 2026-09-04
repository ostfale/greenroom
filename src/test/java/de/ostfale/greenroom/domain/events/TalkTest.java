package de.ostfale.greenroom.domain.events;

import de.ostfale.greenroom.domain.Rule;
import de.ostfale.greenroom.domain.speakers.Speaker;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static de.ostfale.greenroom.Violations.ruleBrokenBy;
import static org.assertj.core.api.Assertions.assertThat;

/** Plain Java, no Spring: the talk carries its own rules. */
class TalkTest {

    private static final TalkSpeaker MAX = TalkSpeaker.of(1L);
    private static final TalkSpeaker ANNA = TalkSpeaker.of(2L);

    @Test
    void aTalkNeedsAtLeastOneSpeaker() {
        assertThat(ruleBrokenBy(() -> new Talk(null, "Records in Java 25", null, Talk.USUALLY,
                List.of(), List.of())))
                .isEqualTo(Rule.TALK_NEEDS_A_SPEAKER);

        assertThat(ruleBrokenBy(() -> new Talk(null, "Records in Java 25", null, Talk.USUALLY,
                null, List.of())))
                .isEqualTo(Rule.TALK_NEEDS_A_SPEAKER);
    }

    /**
     * The usual hour to start with, so the ordinary evening costs nothing. A second talk
     * says its own, and one from ten years ago may say none.
     */
    @Test
    void aTalkBeginsAtTheUsualHourUntilSomebodySaysOtherwise() {
        assertThat(Talk.by(MAX).startsAt()).isEqualTo(LocalTime.of(19, 0));
        assertThat(Talk.by(MAX).withStartsAt(LocalTime.of(20, 15)).startsAt())
                .isEqualTo(LocalTime.of(20, 15));
        assertThat(Talk.by(MAX).withStartsAt(null).startsAt()).isNull();
    }

    @Test
    void theSpeakerCannotBeTakenAwayAgain() {
        assertThat(ruleBrokenBy(() -> Talk.by(MAX).withSpeakers(List.of())))
                .isEqualTo(Rule.TALK_NEEDS_A_SPEAKER);
    }

    @Test
    void theSameSpeakerCannotBeOnTheTalkTwice() {
        assertThat(ruleBrokenBy(() -> Talk.by(MAX).withAdditionalSpeaker(TalkSpeaker.of(1L))))
                .isEqualTo(Rule.SPEAKER_TWICE_ON_TALK);
    }

    @Test
    void aTalkStartsAsNothingButAPersonWeWantToHear() {
        Talk talk = Talk.by(MAX);

        assertThat(talk.title()).isNull();
        assertThat(talk.abstractText()).isNull();
        assertThat(talk.speakers()).containsExactly(MAX);
    }

    @Test
    void aSecondVoiceCanBeAddedLater() {
        Talk talk = Talk.by(MAX).withAdditionalSpeaker(ANNA);

        assertThat(talk.speakers()).containsExactly(MAX, ANNA);
        assertThat(talk.isGivenBy(2L)).isTrue();
        assertThat(talk.isGivenBy(3L)).isFalse();
    }

    @Test
    void onlyATitleAndAnAbstractMakeItReadyToPublish() {
        Talk talk = Talk.by(MAX);

        assertThat(talk.isReadyToPublish()).isFalse();
        assertThat(talk.withTitle("Records in Java 25").isReadyToPublish()).isFalse();
        assertThat(talk.withAbstract("Warum Records mehr sind als weniger Tippen.").isReadyToPublish()).isFalse();
        assertThat(talk.withTitle("Records in Java 25")
                .withAbstract("Warum Records mehr sind als weniger Tippen.")
                .isReadyToPublish()).isTrue();
    }

    @Test
    void aBlankTitleIsNoTitle() {
        Talk talk = Talk.by(MAX).withTitle("  ").withAbstract(" ");

        assertThat(talk.title()).isNull();
        assertThat(talk.abstractText()).isNull();
        assertThat(talk.isReadyToPublish()).isFalse();
    }

    @Test
    void surroundingWhitespaceIsStripped() {
        Talk talk = Talk.by(MAX).withTitle("  Records in Java 25 ");

        assertThat(talk.title()).isEqualTo("Records in Java 25");
    }

    @Test
    void speakersAreNeverSharedWithTheCaller() {
        List<TalkSpeaker> mutable = new ArrayList<>(List.of(MAX));
        Talk talk = Talk.by(MAX).withSpeakers(mutable);
        mutable.clear();

        assertThat(talk.speakers()).containsExactly(MAX);
    }

    @Test
    void announcingASpeakerCopiesTheBiographyAsItIsNow() {
        Speaker stored = new Speaker(7L, "Max Muster", "Musterfirma GmbH", "max@example.org",
                null, "Schreibt Java, seit es Generics gibt.", null, List.of());

        TalkSpeaker announced = TalkSpeaker.announcing(stored);

        assertThat(announced.speakerId()).isEqualTo(7L);
        assertThat(announced.announcedBio()).isEqualTo("Schreibt Java, seit es Generics gibt.");
    }

    @Test
    void theCopiedBiographyDoesNotFollowTheSpeakerAnyMore() {
        Speaker stored = new Speaker(7L, "Max Muster", null, "max@example.org", null,
                "Alte Vita.", null, List.of());
        TalkSpeaker announced = TalkSpeaker.announcing(stored);

        Speaker rewritten = stored.withBio("Neue Vita.");

        assertThat(rewritten.bio()).isEqualTo("Neue Vita.");
        assertThat(announced.announcedBio()).isEqualTo("Alte Vita.");
    }

    @Test
    void aSpeakerThatWasNeverStoredCannotBeAnnounced() {
        assertThat(ruleBrokenBy(() -> TalkSpeaker.announcing(Speaker.of("Max Muster", "max@example.org"))))
                .isEqualTo(Rule.SPEAKER_NOT_STORED);
    }

    @Test
    void aTalkSpeakerAlwaysPointsAtASpeaker() {
        assertThat(ruleBrokenBy(() -> TalkSpeaker.of(null)))
                .isEqualTo(Rule.SPEAKER_NOT_STORED);
    }

    // --- tags ---------------------------------------------------------------------------

    /**
     * The words sit on the talk because it is the talk that is about something. They are
     * copied off the maintained list and never referenced: renaming a tag in the settings
     * must not rewrite what an evening was announced with.
     */
    @Test
    void theKeywordsAreCopiedOntoTheTalkNotReferenced() {
        Talk talk = Talk.by(MAX).withTags(List.of("Java", "Records"));

        assertThat(talk.tags()).containsExactly("Java", "Records");
        assertThat(talk.carries("java")).isTrue();
        assertThat(talk.carries("Testing")).isFalse();
    }

    /** No keyword was written down: that is none, not a list that is missing. */
    @Test
    void aTalkWithoutKeywordsCarriesAnEmptyListRatherThanNone() {
        Talk read = new Talk(1L, "Records in Java 25", null, Talk.USUALLY, List.of(MAX), null);

        assertThat(read.tags()).isEmpty();
    }

    @Test
    void theSameKeywordCannotBeOnTheTalkTwice() {
        assertThat(ruleBrokenBy(() -> Talk.by(MAX).withTags(List.of("Spring", "spring"))))
                .isEqualTo(Rule.TAG_TWICE_ON_TALK);
    }

    @Test
    void aKeywordIsAWordOrItIsNotThere() {
        assertThat(ruleBrokenBy(() -> Talk.by(MAX).withTags(List.of("  "))))
                .isEqualTo(Rule.TAG_NEEDS_A_WORD);
        assertThat(Talk.by(MAX).withTags(List.of(" Java ")).tags()).containsExactly("Java");
    }

    @Test
    void tagsAreNeverSharedWithTheCaller() {
        List<String> mutable = new ArrayList<>(List.of("Java"));
        Talk talk = Talk.by(MAX).withTags(mutable);
        mutable.clear();

        assertThat(talk.tags()).containsExactly("Java");
    }
}
