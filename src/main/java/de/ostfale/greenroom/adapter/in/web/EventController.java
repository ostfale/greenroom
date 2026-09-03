package de.ostfale.greenroom.adapter.in.web;

import de.ostfale.greenroom.application.port.in.EventFilter;
import de.ostfale.greenroom.application.port.in.ManageActivities;
import de.ostfale.greenroom.application.port.in.ManageEvents;
import de.ostfale.greenroom.application.port.in.ManageLocations;
import de.ostfale.greenroom.application.port.in.ManageSpeakers;
import de.ostfale.greenroom.application.port.in.ManageTags;
import de.ostfale.greenroom.domain.Rule;
import de.ostfale.greenroom.domain.RuleViolated;
import de.ostfale.greenroom.domain.activities.Activity;
import de.ostfale.greenroom.domain.activities.ActivityKind;
import de.ostfale.greenroom.domain.events.Event;
import de.ostfale.greenroom.domain.events.EventMode;
import de.ostfale.greenroom.domain.events.EventStatus;
import de.ostfale.greenroom.domain.events.Talk;
import de.ostfale.greenroom.domain.events.TalkSpeaker;
import de.ostfale.greenroom.domain.locations.Location;
import de.ostfale.greenroom.domain.speakers.Speaker;
import de.ostfale.greenroom.domain.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
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
    private final ManageTags tags;
    private final ManageActivities activities;
    private final ErrorMessages errors;

    public EventController(ManageEvents events, ManageSpeakers speakers, ManageLocations locations,
                           ManageTags tags, ManageActivities activities, ErrorMessages errors) {
        this.events = events;
        this.speakers = speakers;
        this.locations = locations;
        this.tags = tags;
        this.activities = activities;
        this.errors = errors;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "") String search,
                       @RequestParam(defaultValue = "false") boolean hideClosed,
                       @RequestParam(required = false) String year,
                       @RequestParam(defaultValue = "") String speakerId,
                       @RequestParam(defaultValue = "") String locationId,
                       @RequestParam(name = "tag", required = false) List<String> tag,
                       Model model) {
        fill(model, narrowedTo(search, hideClosed, year, speakerId, locationId, tag));
        return "event/list";
    }

    /**
     * The same route for htmx: only the table comes back. The filter bar is not swapped
     * with it, so the selects keep what was picked.
     */
    @GetMapping(headers = "HX-Request")
    public String listFragment(@RequestParam(defaultValue = "") String search,
                               @RequestParam(defaultValue = "false") boolean hideClosed,
                               @RequestParam(required = false) String year,
                               @RequestParam(defaultValue = "") String speakerId,
                               @RequestParam(defaultValue = "") String locationId,
                               @RequestParam(name = "tag", required = false) List<String> tag,
                               Model model) {
        fill(model, narrowedTo(search, hideClosed, year, speakerId, locationId, tag));
        return "fragments/event-table :: event-table";
    }

    /** An empty select is not a value but the absence of one, so it narrows nothing. */
    private static EventFilter narrowedTo(String search, boolean hideClosed, String year,
                                          String speakerId, String locationId,
                                          List<String> tag) {
        return new EventFilter(search, hideClosed, yearOrThisOne(year), number(speakerId),
                number(locationId), tag);
    }

    /**
     * No parameter at all means this year: the list opens on what is being planned. An
     * empty one is the "Alle Jahre" somebody picked, and the form sends it from then on —
     * which is why the two cases have to stay apart.
     */
    private static Integer yearOrThisOne(String year) {
        if (year == null) {
            return LocalDate.now().getYear();
        }
        Long picked = number(year);
        return picked == null ? null : picked.intValue();
    }

    /** What a select sends, or {@code null} when it was left on "alle". */
    private static Long number(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.strip());
        } catch (NumberFormatException e) {
            return null;
        }
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
            Talk talk = Talk.by(announced(speakerId)).withTitle(title);
            events.add(Event.draftFor(talk).withDate(evening(date)));
            return "redirect:/event";
        } catch (RuleViolated e) {
            // The records know the rules; the form only has to say so in German and keep
            // what was typed.
            model.addAttribute("error", errors.german(e));
            model.addAttribute("submitted", submitted(speakerId, title, date));
            model.addAttribute("speakers", speakers.all());
            return "event/form";
        }
    }

    /**
     * A talk speaker with the biography they have right now. The copy is taken here, at
     * the moment the person is put on the talk: what the evening announced stays, however
     * often they rewrite their bio afterwards.
     */
    private TalkSpeaker announced(String speakerId) {
        Long id = speaker(speakerId);
        return speakers.byId(id)
                .map(TalkSpeaker::announcing)
                .orElseThrow(() -> new RuleViolated(Rule.NO_SPEAKER_CHOSEN));
    }

    private static Long speaker(String speakerId) {
        if (speakerId == null || speakerId.isBlank()) {
            throw new RuleViolated(Rule.NO_SPEAKER_CHOSEN);
        }
        try {
            return Long.valueOf(speakerId.strip());
        } catch (NumberFormatException e) {
            throw new RuleViolated(Rule.NO_SPEAKER_CHOSEN);
        }
    }

    /** Empty means the date is still open — a topic is allowed to have none. */
    private static LocalDate evening(String date) {
        return FormValues.date(date);
    }

    /**
     * How the evening is held. Empty is what a new evening is: on site. That is the
     * ordinary case, and the years worth entering by hand are the ones that were not.
     */
    private static EventMode heldAs(String mode) {
        if (mode == null || mode.isBlank()) {
            return EventMode.ONSITE;
        }
        try {
            return EventMode.valueOf(mode.strip());
        } catch (IllegalArgumentException e) {
            throw new RuleViolated(Rule.EVENT_NEEDS_A_MODE, mode);
        }
    }

    private void fill(Model model, EventFilter filter) {
        List<Event> all = events.all();
        int thisYear = LocalDate.now().getYear();
        model.addAttribute("events", events.matching(filter));
        model.addAttribute("filter", filter);
        model.addAttribute("thisYear", thisYear);
        model.addAttribute("lastYear", thisYear - 1);
        // What the list opens with is not a filter somebody picked, so it offers no way back.
        model.addAttribute("filtered", !filter.equals(EventFilter.forYear(thisYear)));
        // An empty table because of a filter is a different sentence from an empty database.
        model.addAttribute("anyEvents", !all.isEmpty());
        // A year holds only what has a date, and the topics are the ones worth missing.
        model.addAttribute("topicsHidden", filter.year() == null ? 0
                : all.stream().filter(event -> event.date() == null).count());
        model.addAttribute("speakers", speakers.all());
        model.addAttribute("locations", locations.all());
        model.addAttribute("tagChoices", tagFilterChoices(all));
        model.addAttribute("locationNames", locations.all().stream()
                .collect(Collectors.toMap(Location::id, Location::name)));
    }

    /**
     * Both sources, because either alone leaves something out: the maintained list holds
     * the words that are ready to be used but sit on no evening yet, and the evenings hold
     * the words that were renamed or dropped in the settings and are still what an evening
     * was announced with. Case is ignored, the way an event matches its own tags.
     */
    private List<String> tagFilterChoices(List<Event> all) {
        Set<String> words = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        tags.all().forEach(word -> words.add(word.name()));
        all.forEach(event -> words.addAll(event.tags()));
        return List.copyOf(words);
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
                         @RequestParam(defaultValue = "") String moderator,
                         @RequestParam(defaultValue = "") String mode,
                         @RequestParam(defaultValue = "") String notes,
                         Model model) {
        try {
            Event known = events.byId(id).orElseThrow(() ->
                    new RuleViolated(Rule.NOT_FOUND));
            events.change(known.withDate(evening(date))
                    .withMotto(motto)
                    .withModerator(moderator)
                    .withMode(heldAs(mode))
                    .withNotes(notes));
        } catch (RuleViolated e) {
            model.addAttribute("error", errors.german(e));
        }
        return tile(id, model, "fragments/event-basics :: event-basics");
    }

    /**
     * The evening for somebody's own calendar. A file rather than a subscription: this
     * runs in a home network and is reachable by nobody a calendar could poll.
     */
    @GetMapping("/{id}/ical")
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

    /**
     * One step on. Only the target is sent: which status the evening has is read from the
     * database, so a page left open overnight cannot talk it into a step it never had.
     */
    @PostMapping("/{id}/status")
    public String moveTo(@PathVariable Long id, @RequestParam EventStatus target, Model model) {
        try {
            events.moveTo(id, target);
        } catch (RuleViolated e) {
            model.addAttribute("error", errors.german(e));
        }
        return tile(id, model, "fragments/event-status :: event-status");
    }

    /**
     * The host. Empty means the evening has none yet — or lost the one it had, which the
     * record refuses once the status promises a venue.
     */
    @PostMapping("/{id}/location")
    public String assignVenue(@PathVariable Long id,
                              @RequestParam(defaultValue = "") String locationId,
                              @RequestParam(defaultValue = "") String addressPosition,
                              Model model) {
        try {
            Event known = events.byId(id).orElseThrow(() ->
                    new RuleViolated(Rule.NOT_FOUND));
            Long place = venue(locationId);
            // withLocation drops a pin that belonged to another place, so the address is
            // set after it and never before.
            events.change(known.withLocation(place)
                    .withAddressAt(addressAt(place, addressPosition)));
        } catch (RuleViolated e) {
            model.addAttribute("error", errors.german(e));
        }
        return tile(id, model, "fragments/event-venue :: event-venue");
    }

    /**
     * The addresses of the place that was just picked. Its own little route because the
     * second select depends on the first, and htmx swaps it rather than the whole tile.
     */
    @GetMapping("/{id}/addresses")
    public String venueAddresses(@PathVariable Long id,
                                 @RequestParam(defaultValue = "") String locationId,
                                 Model model) {
        Long place = venue(locationId);
        model.addAttribute("place", place == null ? null : locations.byId(place).orElse(null));
        // Another place means another list, so nothing is preselected in it.
        model.addAttribute("chosenAddress", null);
        return "fragments/event-venue :: venue-address";
    }

    /**
     * Which of the venue's addresses, checked against the place it belongs to: a position
     * that is not there is a stale page or a tampered form, and either way not an address.
     */
    private Integer addressAt(Long place, String position) {
        if (place == null || position == null || position.isBlank()) {
            return null;
        }
        int picked;
        try {
            picked = Integer.parseInt(position.strip());
        } catch (NumberFormatException e) {
            throw new RuleViolated(Rule.NO_ADDRESS_AT_POSITION, position);
        }
        // Asked so it refuses here rather than on the page that reads it back.
        locations.byId(place).orElseThrow(() -> new RuleViolated(Rule.NOT_FOUND))
                .addressAt(picked);
        return picked;
    }

    /** Empty is a valid answer here: an evening may well have no venue yet. */
    private static Long venue(String locationId) {
        if (locationId == null || locationId.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(locationId.strip());
        } catch (NumberFormatException e) {
            throw new RuleViolated(Rule.NO_LOCATION_CHOSEN);
        }
    }

    /**
     * The words this evening is announced with. Copied from the list in the settings, never
     * referenced: renaming or deleting a tag later must not rewrite what an evening was.
     */
    @PostMapping("/{id}/tags")
    public String changeTags(@PathVariable Long id,
                             @RequestParam(name = "tag", required = false) List<String> chosen,
                             Model model) {
        try {
            Event known = events.byId(id).orElseThrow(() ->
                    new RuleViolated(Rule.NOT_FOUND));
            events.change(known.withTags(chosen == null ? List.of() : chosen));
        } catch (RuleViolated e) {
            model.addAttribute("error", errors.german(e));
        }
        return tile(id, model, "fragments/event-tags :: event-tags");
    }

    /** A further talk. Like the first one, it comes into being with its speaker. */
    @PostMapping("/{id}/talk")
    public String addTalk(@PathVariable Long id,
                          @RequestParam(defaultValue = "") String speakerId,
                          @RequestParam(defaultValue = "") String title,
                          @RequestParam(defaultValue = "") String startsAt,
                          Model model) {
        return talks(id, model, () -> events.addTalk(id, Talk.by(announced(speakerId))
                .withTitle(title)
                .withStartsAt(FormValues.time(startsAt))));
    }

    @PostMapping("/{id}/talk/{position}")
    public String changeTalk(@PathVariable Long id,
                             @PathVariable int position,
                             @RequestParam(defaultValue = "") String title,
                             @RequestParam(defaultValue = "") String abstractText,
                             @RequestParam(defaultValue = "") String startsAt,
                             @RequestParam(name = "announcedBio", required = false) List<String> bios,
                             Model model) {
        return talks(id, model, () -> events.changeTalk(id, position, title, abstractText,
                FormValues.time(startsAt), bios));
    }

    @PostMapping("/{id}/talk/{position}/remove")
    public String removeTalk(@PathVariable Long id, @PathVariable int position, Model model) {
        return talks(id, model, () -> events.removeTalk(id, position));
    }

    /**
     * Every change to the talks answers with the same list. On a refusal the stored state
     * comes back unchanged, together with the reason.
     */
    private String talks(Long id, Model model, Supplier<Event> change) {
        try {
            change.get();
        } catch (RuleViolated e) {
            model.addAttribute("error", errors.german(e));
        }
        return tile(id, model, "fragments/event-talks :: event-talks");
    }

    /**
     * One more line in the history, and the only way one comes into being: nothing in the
     * application writes a line by itself. Append-only, so there is no counterpart that
     * changes or drops one — a line that was wrong is answered by the next line.
     */
    @PostMapping("/{id}/activity")
    public String appendActivity(@PathVariable Long id,
                                 @RequestParam(defaultValue = "") String happenedOn,
                                 @RequestParam(defaultValue = "") String kind,
                                 @RequestParam(defaultValue = "") String what,
                                 Model model) {
        try {
            activities.append(Activity.of(id, day(happenedOn), asKind(kind), what));
        } catch (RuleViolated e) {
            model.addAttribute("error", errors.german(e));
        }
        return tile(id, model, "fragments/event-history :: event-history");
    }

    private static ActivityKind asKind(String kind) {
        try {
            return ActivityKind.valueOf(kind.strip());
        } catch (RuntimeException e) {
            throw new RuleViolated(Rule.ACTIVITY_NEEDS_A_KIND);
        }
    }

    /** Empty means today: a line is written down right after the mail went or came. */
    private static LocalDate day(String date) {
        if (date == null || date.isBlank()) {
            return LocalDate.now();
        }
        return evening(date);
    }

    /** The people who speak at this evening — those are the ones there is anything to ask. */
    private static List<Long> speakersOf(Event event) {
        List<Long> asked = new ArrayList<>();
        event.talks().forEach(talk -> talk.speakers().forEach(announced -> {
            if (!asked.contains(announced.speakerId())) {
                asked.add(announced.speakerId());
            }
        }));
        return asked;
    }

    /** Every change answers with the tile it was made in, showing what is stored now. */
    private String tile(Long id, Model model, String fragment) {
        events.byId(id).ifPresent(event -> show(model, event));
        return fragment;
    }

    private void show(Model model, Event event) {
        List<Location> places = locations.all();
        model.addAttribute("event", event);
        model.addAttribute("transitions", event.status().allowedTargets());
        model.addAttribute("modes", EventMode.values());
        // Only what may still be chosen — plus the place this evening already sits at, so
        // an evening at a venue we gave up still shows it rather than losing it silently.
        model.addAttribute("locations", places.stream()
                .filter(place -> place.inUse() || place.id().equals(event.locationId()))
                .toList());
        model.addAttribute("clashes", events.clashesWith(event));
        model.addAttribute("tagChoices", tagChoices(event));
        List<Speaker> known = speakers.all();
        // In the order of the talks, not alphabetically: the evening reads that way.
        model.addAttribute("eventSpeakers", speakersOf(event).stream()
                .flatMap(speaking -> known.stream().filter(one -> one.id().equals(speaking)))
                .toList());
        model.addAttribute("history", activities.historyOf(event.id()));
        model.addAttribute("kinds", ActivityKind.values());
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("speakers", known);
        // The talks name their speakers by id, and a page shows people by name.
        model.addAttribute("speakerNames", known.stream()
                .collect(Collectors.toMap(Speaker::id, Speaker::name)));
        Location host = locations.byId(event.locationId()).orElse(null);
        model.addAttribute("location", host);
        // The address select reads these; the same fragment is served on its own when the
        // place changes, and then they come from the request instead.
        model.addAttribute("place", host);
        model.addAttribute("chosenAddress", event.addressPosition());
    }

    /**
     * Every word that may be ticked: the maintained list, and on top of it whatever this
     * evening already carries. A tag deleted from the settings must not silently fall off
     * an evening that was announced with it.
     */
    private List<String> tagChoices(Event event) {
        List<String> choices = new ArrayList<>(tags.all().stream().map(Tag::name).toList());
        for (String own : event.tags()) {
            if (choices.stream().noneMatch(word -> word.equalsIgnoreCase(own))) {
                choices.add(own);
            }
        }
        return choices;
    }
}
