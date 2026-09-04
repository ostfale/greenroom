package de.ostfale.greenroom.adapter.in.web;

import de.ostfale.greenroom.application.port.in.ManageEvents;
import de.ostfale.greenroom.application.port.in.ManageLocations;
import de.ostfale.greenroom.application.port.in.ManageSpeakers;
import de.ostfale.greenroom.domain.events.Event;
import de.ostfale.greenroom.domain.speakers.Speaker;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.stream.Collectors;

/**
 * The evening for somebody's own calendar. A file rather than a subscription: this runs in
 * a home network and is reachable by nobody a calendar could poll.
 *
 * <p>Its own controller because it is the one route of the evening that answers with a
 * file instead of a page — no model, no tile, no fragment. What the file is made of is
 * {@link CalendarEntry}.
 */
@Controller
@RequestMapping("/event/{id}/ical")
public class EventCalendarController {

    private final ManageEvents events;
    private final ManageSpeakers speakers;
    private final ManageLocations locations;
    private final ErrorMessages errors;

    public EventCalendarController(ManageEvents events, ManageSpeakers speakers,
                                   ManageLocations locations, ErrorMessages errors) {
        this.events = events;
        this.speakers = speakers;
        this.locations = locations;
        this.errors = errors;
    }

    /** Nothing to hand out for an evening that has no day yet; the page offers no link either. */
    @GetMapping
    @ResponseBody
    public ResponseEntity<String> ical(@PathVariable Long id) {
        Event known = events.byId(id).orElse(null);
        if (known == null || known.date() == null) {
            return ResponseEntity.notFound().build();
        }
        String file = CalendarEntry.of(known, known.displayName() != null
                        ? known.displayName()
                        : errors.text("ical.untitled"),
                locations.byId(known.locationId()).orElse(null),
                speakers.all().stream()
                        .collect(Collectors.toMap(Speaker::id, Speaker::name)),
                Instant.now());
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "calendar", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(CalendarEntry.fileName(known)).build().toString())
                .body(file);
    }
}
