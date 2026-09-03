package de.ostfale.greenroom.adapter.in.web;

import de.ostfale.greenroom.domain.events.Event;
import de.ostfale.greenroom.domain.events.EventStatus;
import de.ostfale.greenroom.domain.events.Talk;
import de.ostfale.greenroom.domain.locations.Location;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * The evening as an iCalendar file, for whoever asks to put it in their own calendar.
 * Built here and not in the domain, for the reason {@link MapExcerpt} is: a wire format is
 * rendering, and an aggregate has no business knowing one.
 *
 * <p>Timed where the evening says when it begins, and an all-day banner where it does not.
 * No end either way: a talk has no duration in this model, and inventing three hours here
 * would put a fact in the export that stands nowhere else. A calendar reads a start without
 * an end as a mark at that hour, which is exactly what is known.
 */
final class CalendarEntry {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    /** The one the evenings happen in, and the one the application runs in. */
    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    /** RFC 5545 folds at 75 octets; a continuation begins with one space. */
    private static final int FOLD_AT = 75;

    private CalendarEntry() {
    }

    /**
     * One VEVENT, or null when the evening has no date — there is nothing to put in a
     * calendar then, and the page offers no link either.
     *
     * @param name         what the entry is called — handed in, because the fallback for
     *                     an evening that has no name yet is German and lives in the bundle
     * @param place        where it is held, or null while no venue is assigned
     * @param speakerNames the names to write next to the talks, by speaker id
     */
    static String of(Event event, String name, Location place, Map<Long, String> speakerNames,
                     Instant now) {
        if (event.date() == null) {
            return null;
        }
        List<String> lines = new ArrayList<>();
        lines.add("BEGIN:VCALENDAR");
        lines.add("VERSION:2.0");
        lines.add("PRODID:-//greenroom//JUG Hamburg//DE");
        lines.add("CALSCALE:GREGORIAN");
        lines.add("BEGIN:VEVENT");
        // Stable across exports, so a second import corrects the entry instead of adding one.
        lines.add("UID:event-" + event.id() + "@greenroom");
        lines.add("DTSTAMP:" + STAMP.format(now));
        if (event.startsAt() == null) {
            lines.add("DTSTART;VALUE=DATE:" + DAY.format(event.date()));
            // The end of an all-day entry is the day after it: the range excludes it.
            lines.add("DTEND;VALUE=DATE:" + DAY.format(event.date().plusDays(1)));
        } else {
            // As an instant in UTC rather than a local time with a TZID: naming a zone
            // obliges the file to carry a VTIMEZONE block defining it, and a moment needs
            // no definition. No DTEND — nothing here knows when the evening is over.
            lines.add("DTSTART:" + STAMP.format(event.date().atTime(event.startsAt())
                    .atZone(ZONE).toInstant()));
        }
        lines.add("SUMMARY:" + escaped(name));
        if (place != null) {
            lines.add("LOCATION:" + escaped(where(place)));
        }
        String talks = talks(event, speakerNames);
        if (!talks.isEmpty()) {
            lines.add("DESCRIPTION:" + escaped(talks));
        }
        lines.add("STATUS:"
                + (event.status() == EventStatus.CANCELLED ? "CANCELLED" : "CONFIRMED"));
        lines.add("END:VEVENT");
        lines.add("END:VCALENDAR");

        StringBuilder file = new StringBuilder();
        lines.forEach(line -> file.append(folded(line)).append("\r\n"));
        return file.toString();
    }

    /** What the file is called when it is saved. */
    static String fileName(Event event) {
        return "greenroom-" + event.date() + ".ics";
    }

    private static String where(Location place) {
        String address = place.addressLine();
        return address.isEmpty() ? place.name() : place.name() + ", " + address;
    }

    /** One line per talk: what is given, and by whom. No sentence — a list is not prose. */
    private static String talks(Event event, Map<Long, String> speakerNames) {
        StringJoiner lines = new StringJoiner("\n");
        for (Talk talk : event.talks()) {
            String who = talk.speakers().stream()
                    .map(announced -> speakerNames.getOrDefault(announced.speakerId(), ""))
                    .filter(one -> !one.isBlank())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
            String title = talk.title() == null ? "" : talk.title();
            if (title.isEmpty() && who.isEmpty()) {
                continue;
            }
            lines.add(title.isEmpty() ? who : who.isEmpty() ? title : title + " — " + who);
        }
        return lines.toString();
    }

    /** What a value may not carry unescaped: the separators, and the line break itself. */
    private static String escaped(String value) {
        return value.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\n", "\\n");
    }

    /**
     * Folded on octets rather than characters: an umlaut is two bytes in UTF-8, and a fold
     * that counted characters would put the break in the middle of one.
     */
    private static String folded(String line) {
        StringBuilder folded = new StringBuilder();
        int octets = 0;
        for (int i = 0; i < line.length(); ) {
            int point = line.codePointAt(i);
            int width = new String(Character.toChars(point))
                    .getBytes(StandardCharsets.UTF_8).length;
            if (octets + width > FOLD_AT) {
                folded.append("\r\n ");
                octets = 1;
            }
            folded.appendCodePoint(point);
            octets += width;
            i += Character.charCount(point);
        }
        return folded.toString();
    }
}
