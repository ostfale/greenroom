package de.ostfale.greenroom.application.port.in;

import de.ostfale.greenroom.domain.events.Event;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static de.ostfale.greenroom.Fixtures.EVENING;
import static de.ostfale.greenroom.Fixtures.aReadyTalk;
import static org.assertj.core.api.Assertions.assertThat;

/** Plain Java, no Spring: what each field lets through, and how they add up. */
class EventFilterTest {

    private static final Long MAX = 1L;
    private static final Long ANNA = 2L;
    private static final Long PLACE = 7L;

    private static Event dated() {
        return Event.draftFor(aReadyTalk(MAX)).withDate(EVENING);
    }

    @Test
    void withoutAnyFieldEverythingPasses() {
        EventFilter none = EventFilter.none();

        assertThat(none.isSet()).isFalse();
        assertThat(none.matches(dated())).isTrue();
        assertThat(none.matches(Event.draftFor(aReadyTalk(MAX)))).isTrue();
    }

    @Test
    void theYearHoldsOnlyWhatFallsInIt() {
        EventFilter in2026 = new EventFilter(null, false, 2026, null, null, List.of());

        assertThat(in2026.matches(dated())).isTrue();
        assertThat(in2026.matches(dated().withDate(LocalDate.of(2025, 9, 24)))).isFalse();
    }

    /** A topic belongs to no year yet, which is not the same as belonging to all of them. */
    @Test
    void aTopicWithoutADateFallsOutOfEveryYear() {
        Event topic = Event.draftFor(aReadyTalk(MAX));

        assertThat(new EventFilter(null, false, 2026, null, null, List.of()).matches(topic)).isFalse();
        assertThat(EventFilter.none().matches(topic)).isTrue();
    }

    @Test
    void theSpeakerIsLookedForOnEveryTalk() {
        Event two = dated().withAdditionalTalk(aReadyTalk(ANNA));

        assertThat(new EventFilter(null, false, null, ANNA, null, List.of()).matches(two)).isTrue();
        assertThat(new EventFilter(null, false, null, ANNA, null, List.of()).matches(dated())).isFalse();
    }

    @Test
    void thePlaceIsTheOneTheEveningIsAt() {
        assertThat(new EventFilter(null, false, null, null, PLACE, List.of()).matches(dated().withLocation(PLACE)))
                .isTrue();
        assertThat(new EventFilter(null, false, null, null, PLACE, List.of()).matches(dated())).isFalse();
    }

    /** Matched against the words the evening carries, and case is not one of them. */
    @Test
    void theTagIgnoresCase() {
        Event tagged = dated().withTags(List.of("Spring"));

        assertThat(new EventFilter(null, false, null, null, null, List.of("spring")).matches(tagged)).isTrue();
        assertThat(new EventFilter(null, false, null, null, null, List.of("Java")).matches(tagged)).isFalse();
    }

    @Test
    void theWordsNarrowTheListLikeEveryOtherField() {
        Event evening = dated().withMotto("Alles über arc42");

        assertThat(new EventFilter("arc42", false, null, null, null, List.of()).matches(evening))
                .isTrue();
        assertThat(new EventFilter("arc42", false, null, null, null, List.of()).matches(dated()))
                .isFalse();
    }

    /** Nothing typed is not a filter, and the page offers no way back from it. */
    @Test
    void blankWordsNarrowNothing() {
        EventFilter blank = new EventFilter("   ", false, null, null, null, List.of());

        assertThat(blank.text()).isNull();
        assertThat(blank.isSet()).isFalse();
        assertThat(blank.matches(dated())).isTrue();
    }

    @Test
    void theWordsAddUpWithTheFieldsAroundThem() {
        Event evening = dated().withMotto("Alles über arc42").withLocation(PLACE);

        assertThat(new EventFilter("arc42", false, 2026, null, PLACE, List.of()).matches(evening))
                .isTrue();
        assertThat(new EventFilter("arc42", false, 2025, null, PLACE, List.of()).matches(evening))
                .isFalse();
    }

    @Test
    void aBlankTagNarrowsNothing() {
        EventFilter blank = new EventFilter(null, false, null, null, null, List.of("   "));

        assertThat(blank.tags()).isEmpty();
        assertThat(blank.isSet()).isFalse();
        assertThat(blank.matches(dated())).isTrue();
    }

    /**
     * The one field that widens instead of narrowing: an evening passes when it carries
     * any of the tags, the way a facet works. The fields around it still add up.
     */
    @Test
    void severalTagsLetThroughWhateverCarriesAnyOfThem() {
        Event spring = dated().withTags(List.of("Spring"));
        Event kotlin = dated().withTags(List.of("Kotlin"));
        Event testing = dated().withTags(List.of("Testing"));
        EventFilter either = new EventFilter(null, false, null, null, null, List.of("Spring", "Kotlin"));

        assertThat(either.matches(spring)).isTrue();
        assertThat(either.matches(kotlin)).isTrue();
        assertThat(either.matches(testing)).isFalse();
    }

    /** One of them is enough, even when the evening carries more than was asked for. */
    @Test
    void anEveningWithMoreTagsThanWereAskedForStillPasses() {
        Event both = dated().withTags(List.of("Spring", "Testing"));

        assertThat(new EventFilter(null, false, null, null, null, List.of("Spring")).matches(both))
                .isTrue();
    }

    @Test
    void theFieldsAddUp() {
        Event tagged = dated().withLocation(PLACE).withTags(List.of("Spring"));
        EventFilter both = new EventFilter(null, false, 2026, MAX, PLACE, List.of("Spring"));

        assertThat(both.matches(tagged)).isTrue();
        assertThat(both.matches(tagged.withLocation(9L))).isFalse();
    }

    /**
     * The page offers the way back only when something is narrowed, so every field has to
     * count on its own — the list opens on a year, and that year is a filter like any other.
     */
    @Test
    void everyFieldCountsAsNarrowedOnItsOwn() {
        assertThat(new EventFilter("arc42", false, null, null, null, List.of()).isSet()).isTrue();
        assertThat(new EventFilter(null, false, 2026, null, null, List.of()).isSet()).isTrue();
        assertThat(new EventFilter(null, false, null, MAX, null, List.of()).isSet()).isTrue();
        assertThat(new EventFilter(null, false, null, null, PLACE, List.of()).isSet()).isTrue();
        assertThat(new EventFilter(null, false, null, null, null, List.of("Spring")).isSet()).isTrue();
    }

    @Test
    void hidingWhatIsOverIsJustAnotherField() {
        EventFilter open = new EventFilter(null, true, null, null, null, List.of());

        assertThat(open.isSet()).isTrue();
        assertThat(open.matches(dated())).isTrue();
        assertThat(open.matches(dated().moveTo(de.ostfale.greenroom.domain.events.EventStatus.CANCELLED)))
                .isFalse();
    }
}
