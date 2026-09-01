package de.ostfale.greenroom.adapter.in.web;

import de.ostfale.greenroom.application.port.in.ManageEvents;
import de.ostfale.greenroom.application.port.in.ManageLocations;
import de.ostfale.greenroom.application.port.in.ManageSpeakers;
import de.ostfale.greenroom.domain.events.Event;
import de.ostfale.greenroom.domain.events.EventStatus;
import de.ostfale.greenroom.domain.events.Talk;
import de.ostfale.greenroom.domain.events.TalkSpeaker;
import de.ostfale.greenroom.domain.locations.Location;
import de.ostfale.greenroom.domain.speakers.Speaker;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The evenings. Creating one means creating its first talk with its speaker at the same
 * time — an event without a talk, and a talk without a speaker, do not exist.
 */
@Controller
@RequestMapping("/event")
public class EventController {

    private final ManageEvents events;
    private final ManageSpeakers speakers;
    private final ManageLocations locations;

    public EventController(ManageEvents events, ManageSpeakers speakers, ManageLocations locations) {
        this.events = events;
        this.speakers = speakers;
        this.locations = locations;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "false") boolean hideClosed, Model model) {
        fill(model, hideClosed);
        return "event/list";
    }

    /** The same route for htmx: only the table comes back when the filter is toggled. */
    @GetMapping(headers = "HX-Request")
    public String listFragment(@RequestParam(defaultValue = "false") boolean hideClosed, Model model) {
        fill(model, hideClosed);
        return "fragments/event-table :: event-table";
    }

    @GetMapping("/new")
    public String form(Model model) {
        model.addAttribute("submitted", submitted("", "", ""));
        model.addAttribute("speakers", speakers.all());
        return "event/form";
    }

    @PostMapping
    public String add(@RequestParam(defaultValue = "") String speakerId,
                      @RequestParam(defaultValue = "") String title,
                      @RequestParam(defaultValue = "") String date,
                      Model model) {
        try {
            Talk talk = Talk.by(TalkSpeaker.of(speaker(speakerId))).withTitle(title);
            events.add(Event.draftFor(talk).withDate(evening(date)));
            return "redirect:/event";
        } catch (IllegalArgumentException e) {
            // The records know the rules; the form only has to say so in German and keep
            // what was typed.
            model.addAttribute("error", message(e));
            model.addAttribute("submitted", submitted(speakerId, title, date));
            model.addAttribute("speakers", speakers.all());
            return "event/form";
        }
    }

    private static Long speaker(String speakerId) {
        if (speakerId == null || speakerId.isBlank()) {
            throw new IllegalArgumentException("Event :: no speaker was chosen");
        }
        try {
            return Long.valueOf(speakerId.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Event :: no speaker was chosen");
        }
    }

    /** Empty means the date is still open — a topic is allowed to have none. */
    private static LocalDate evening(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(date.strip());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Event :: the date is not a date: " + date);
        }
    }

    private static String message(IllegalArgumentException e) {
        String reason = e.getMessage() == null ? "" : e.getMessage();
        if (reason.contains("not a date")) {
            return "Das Datum konnte nicht gelesen werden.";
        }
        return "Bitte einen Referenten auswählen.";
    }

    private void fill(Model model, boolean hideClosed) {
        model.addAttribute("events", hideClosed ? events.allStillOpen() : events.all());
        model.addAttribute("hideClosed", hideClosed);
        model.addAttribute("locationNames", locations.all().stream()
                .collect(Collectors.toMap(Location::id, Location::name)));
    }

    private static Map<String, String> submitted(String speakerId, String title, String date) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("speakerId", speakerId);
        values.put("title", title);
        values.put("date", date);
        return values;
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        return events.byId(id)
                .map(event -> {
                    show(model, event);
                    return "event/detail";
                })
                .orElse("redirect:/event");
    }

    /**
     * Date and motto — the two things an evening carries on its own. Everything the status
     * promises beyond them is a step of its own.
     */
    @PostMapping("/{id}")
    public String change(@PathVariable Long id,
                         @RequestParam(defaultValue = "") String date,
                         @RequestParam(defaultValue = "") String motto,
                         Model model) {
        try {
            Event known = events.byId(id).orElseThrow(() ->
                    new IllegalArgumentException("EventController :: unknown event"));
            events.change(known.withDate(evening(date)).withMotto(motto));
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", planningMessage(e));
        }
        return tile(id, model, "fragments/event-basics :: event-basics");
    }

    /**
     * One step on. Only the target is sent: which status the evening has is read from the
     * database, so a page left open overnight cannot talk it into a step it never had.
     */
    @PostMapping("/{id}/status")
    public String moveTo(@PathVariable Long id, @RequestParam EventStatus target, Model model) {
        try {
            events.moveTo(id, target);
        } catch (IllegalStateException | IllegalArgumentException e) {
            model.addAttribute("error", planningMessage(e));
        }
        return tile(id, model, "fragments/event-status :: event-status");
    }

    /** Every change answers with the tile it was made in, showing what is stored now. */
    private String tile(Long id, Model model, String fragment) {
        events.byId(id).ifPresent(event -> show(model, event));
        return fragment;
    }

    private void show(Model model, Event event) {
        model.addAttribute("event", event);
        model.addAttribute("transitions", event.status().allowedTargets());
        model.addAttribute("speakerNames", speakers.all().stream()
                .collect(Collectors.toMap(Speaker::id, Speaker::name)));
        locations.byId(event.locationId())
                .ifPresent(location -> model.addAttribute("location", location));
    }

    /** The records refuse in English; the page has to say in German what is missing. */
    private static String planningMessage(RuntimeException e) {
        String reason = e.getMessage() == null ? "" : e.getMessage();
        if (reason.contains("does not move to")) {
            return "Dieser Schritt ist von hier aus nicht möglich.";
        }
        if (reason.contains("needs a date")) {
            return "Dafür braucht das Event ein Datum.";
        }
        if (reason.contains("needs a location")) {
            return "Dafür braucht das Event einen Ort.";
        }
        if (reason.contains("needs a title and an abstract")) {
            return "Dafür braucht jeder Vortrag einen Titel und eine Beschreibung.";
        }
        if (reason.contains("not a date")) {
            return "Das Datum konnte nicht gelesen werden.";
        }
        return "Die Änderung wurde nicht übernommen.";
    }
}
