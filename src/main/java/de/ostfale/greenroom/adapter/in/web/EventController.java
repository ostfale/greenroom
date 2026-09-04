package de.ostfale.greenroom.adapter.in.web;

import de.ostfale.greenroom.application.port.in.EventFilter;
import de.ostfale.greenroom.application.port.in.ManageEvents;
import de.ostfale.greenroom.application.port.in.ManageLocations;
import de.ostfale.greenroom.application.port.in.ManageSpeakers;
import de.ostfale.greenroom.application.port.in.ManageTags;
import de.ostfale.greenroom.domain.Rule;
import de.ostfale.greenroom.domain.RuleViolated;
import de.ostfale.greenroom.domain.events.Event;
import de.ostfale.greenroom.domain.events.EventStatus;
import de.ostfale.greenroom.domain.events.Talk;
import de.ostfale.greenroom.domain.locations.Location;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * The evenings: the list, a new topic, and the tiles that belong to the evening itself —
 * its basics, the step it takes next, and where it is held.
 *
 * <p>Creating one means creating its first talk with its speaker at the same time — an
 * event without a talk, and a talk without a speaker, do not exist.
 *
 * <p>What sits on the same page but is a thing of its own has a controller of its own: the
 * talks, the tags, the history and the calendar file. They answer with a tile of this page
 * and build it from {@link EventPage}, which is also where this one gets it.
 */
@Controller
@RequestMapping("/event")
public class EventController {

    private final ManageEvents events;
    private final ManageSpeakers speakers;
    private final ManageLocations locations;
    private final ManageTags tags;
    private final ErrorMessages errors;
    private final EventPage page;
    private final ChosenAddress chosenAddress;

    public EventController(ManageEvents events, ManageSpeakers speakers, ManageLocations locations,
                           ManageTags tags, ErrorMessages errors, EventPage page,
                           ChosenAddress chosenAddress) {
        this.events = events;
        this.speakers = speakers;
        this.locations = locations;
        this.tags = tags;
        this.errors = errors;
        this.page = page;
        this.chosenAddress = chosenAddress;
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
        return new EventFilter(search, hideClosed, yearOrThisOne(year),
                FormValues.filterNumber(speakerId), FormValues.filterNumber(locationId), tag);
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
        Long picked = FormValues.filterNumber(year);
        return picked == null ? null : picked.intValue();
    }

    @GetMapping("/new")
    public String form(Model model) {
        model.addAttribute("submitted", submitted("", "", ""));
        model.addAttribute("speakers", speakers.all());
        return "event/form";
    }

    /**
     * A new topic. The one refusal on this page that is not answered with a tile: there is
     * no evening to show yet, so the form comes back with what was typed still in it.
     */
    @PostMapping
    public String add(@RequestParam(defaultValue = "") String speakerId,
                      @RequestParam(defaultValue = "") String title,
                      @RequestParam(defaultValue = "") String date,
                      Model model) {
        try {
            Talk talk = Talk.by(page.announced(speakerId)).withTitle(title);
            events.add(Event.draftFor(talk).withDate(FormValues.date(date)));
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
                    page.show(model, event);
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
        return page.afterChanging(id, model, "fragments/event-basics :: event-basics", () -> {
            Event known = events.byId(id).orElseThrow(() -> new RuleViolated(Rule.NOT_FOUND));
            events.change(known.withDate(FormValues.date(date))
                    .withMotto(motto)
                    .withModerator(moderator)
                    .withMode(FormValues.mode(mode))
                    .withNotes(notes));
        });
    }

    /**
     * One step on. Only the target is sent: which status the evening has is read from the
     * database, so a page left open overnight cannot talk it into a step it never had.
     */
    @PostMapping("/{id}/status")
    public String moveTo(@PathVariable Long id, @RequestParam EventStatus target, Model model) {
        return page.afterChanging(id, model, "fragments/event-status :: event-status",
                () -> events.moveTo(id, target));
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
        return page.afterChanging(id, model, "fragments/event-venue :: event-venue", () -> {
            Event known = events.byId(id).orElseThrow(() -> new RuleViolated(Rule.NOT_FOUND));
            Long place = FormValues.locationId(locationId);
            // withLocation drops a pin that belonged to another place, so the address is
            // set after it and never before.
            events.change(known.withLocation(place)
                    .withAddressAt(chosenAddress.of(place, addressPosition)));
        });
    }

    /**
     * The addresses of the place that was just picked. Its own little route because the
     * second select depends on the first, and htmx swaps it rather than the whole tile.
     */
    @GetMapping("/{id}/addresses")
    public String venueAddresses(@PathVariable Long id,
                                 @RequestParam(defaultValue = "") String locationId,
                                 Model model) {
        Long place = FormValues.locationId(locationId);
        model.addAttribute("place", place == null ? null : locations.byId(place).orElse(null));
        // Another place means another list, so nothing is preselected in it.
        model.addAttribute("chosenAddress", null);
        return "fragments/event-venue :: venue-address";
    }
}
