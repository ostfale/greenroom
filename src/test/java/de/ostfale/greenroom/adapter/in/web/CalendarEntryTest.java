package de.ostfale.greenroom.adapter.in.web;

import de.ostfale.greenroom.domain.events.Event;
import de.ostfale.greenroom.domain.events.EventStatus;
import de.ostfale.greenroom.domain.locations.Address;
import de.ostfale.greenroom.domain.locations.Location;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static de.ostfale.greenroom.Fixtures.aContact;
import static de.ostfale.greenroom.Fixtures.aReadyTalk;
import static org.assertj.core.api.Assertions.assertThat;

/** Plain Java, no Spring: what goes into the file, and what a calendar may not be handed. */
class CalendarEntryTest {

    private static final Long SPEAKER = 1L;
    private static final LocalDate EVENING = LocalDate.of(2026, 11, 14);
    private static final Instant NOW = Instant.parse("2026-09-03T07:15:30Z");
    private static final Map<Long, String> NAMES = Map.of(SPEAKER, "Max Muster");

    @Test
    void theEveningBecomesAnAllDayEntryOnItsDay() {
        String file = CalendarEntry.of(anEvening(), "Java-Herbst", null, NAMES, NOW);

        assertThat(lines(file)).contains(
                "BEGIN:VCALENDAR",
                "VERSION:2.0",
                "BEGIN:VEVENT",
                "UID:event-4@greenroom",
                "DTSTAMP:20260903T071530Z",
                "DTSTART;VALUE=DATE:20261114",
                // The range excludes its end, so an all-day entry ends on the next day.
                "DTEND;VALUE=DATE:20261115",
                "SUMMARY:Java-Herbst",
                "STATUS:CONFIRMED",
                "END:VEVENT",
                "END:VCALENDAR");
    }

    /** RFC 5545 asks for CRLF, and a calendar that gets bare newlines reads one long line. */
    @Test
    void everyLineEndsTheWayTheFormatAsksFor() {
        String file = CalendarEntry.of(anEvening(), "Java-Herbst", null, NAMES, NOW);

        assertThat(file).endsWith("END:VCALENDAR\r\n");
        assertThat(file.replace("\r\n", "")).doesNotContain("\n");
    }

    @Test
    void thereIsNothingToExportWithoutADay() {
        Event topic = Event.draftFor(aReadyTalk(SPEAKER));

        assertThat(CalendarEntry.of(topic, "Ohne Termin", null, NAMES, NOW)).isNull();
    }

    @Test
    void theVenueIsTheNameAndTheAddressItSitsAt() {
        Location place = Location.of("Musterfirma GmbH", aContact())
                .movedTo(Address.at("Musterweg 1", "22179", "Hamburg"));

        String file = CalendarEntry.of(anEvening(), "Java-Herbst", place, NAMES, NOW);

        assertThat(lines(file)).anyMatch(line -> line.startsWith("LOCATION:Musterfirma GmbH"))
                .anyMatch(line -> line.contains("Musterweg 1"));
    }

    @Test
    void theTalksAndWhoGivesThemAreTheDescription() {
        String file = CalendarEntry.of(anEvening(), "Java-Herbst", null, NAMES, NOW);

        assertThat(unfolded(file)).contains("DESCRIPTION:Records in Java 25 — Max Muster");
    }

    /** A comma or a semicolon separates values in this format, so a title may carry neither. */
    @Test
    void whatWouldSeparateValuesIsEscaped() {
        Event event = anEvening().withMotto("Records, Streams; und mehr");

        String file = CalendarEntry.of(event, event.displayName(), null, NAMES, NOW);

        assertThat(unfolded(file)).contains("SUMMARY:Records\\, Streams\\; und mehr");
    }

    /** An evening that was called off is called off; one that is over simply happened. */
    @Test
    void onlyACancelledEveningIsCancelledInACalendar() {
        Event dropped = anEvening().moveTo(EventStatus.CANCELLED);

        assertThat(lines(CalendarEntry.of(dropped, "Java-Herbst", null, NAMES, NOW)))
                .contains("STATUS:CANCELLED");
    }

    /**
     * Folded at 75 octets, counted as octets: an umlaut is two bytes, and a fold that
     * counted characters would hand a calendar a line it has to reject.
     */
    @Test
    void aLongLineIsFoldedWhereTheFormatAllowsIt() {
        String motto = "Ärger mit Änderungen ".repeat(6).strip();

        String file = CalendarEntry.of(anEvening().withMotto(motto), motto, null, NAMES, NOW);

        assertThat(Arrays.stream(file.split("\r\n")).map(line -> line.getBytes(StandardCharsets.UTF_8).length))
                .allMatch(length -> length <= 75);
        assertThat(unfolded(file)).contains("SUMMARY:" + motto);
    }

    @Test
    void theFileIsNamedAfterTheDay() {
        assertThat(CalendarEntry.fileName(anEvening())).isEqualTo("greenroom-2026-11-14.ics");
    }

    private static Event anEvening() {
        return new Event(4L, EVENING, "Java-Herbst", null, null, EventStatus.DRAFT,
                de.ostfale.greenroom.domain.events.EventMode.ONSITE, null,
                List.of(aReadyTalk(SPEAKER)), List.of());
    }

    private static List<String> lines(String file) {
        return Arrays.asList(file.split("\r\n"));
    }

    /** What a calendar reads: a continuation line is joined back onto the one before it. */
    private static String unfolded(String file) {
        return file.replace("\r\n ", "");
    }
}
