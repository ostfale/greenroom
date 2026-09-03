package de.ostfale.greenroom.adapter.in.web;

import de.ostfale.greenroom.application.port.in.ManageEvents;
import de.ostfale.greenroom.application.port.in.ManageLocations;
import de.ostfale.greenroom.application.port.in.ManageSpeakers;
import de.ostfale.greenroom.application.port.in.PastEvening;
import de.ostfale.greenroom.domain.Rule;
import de.ostfale.greenroom.domain.RuleViolated;
import de.ostfale.greenroom.domain.events.EventMode;
import de.ostfale.greenroom.domain.events.EventStatus;
import de.ostfale.greenroom.domain.speakers.Speaker;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The evenings that already happened, written down one form at a time. Nothing of how they
 * were organised is entered with them — who was asked and when is over, and the tool only
 * has to know that the evening took place.
 *
 * <p>Its own controller rather than another route on {@link EventController}: this builds a
 * finished evening in one step, where that one takes a topic apart into small changes. Four
 * status clicks per evening is the right ceremony while an evening is being planned and
 * pure friction for a backlog of a hundred and more.
 *
 * <p>The speaker and the place are picked from what is already there; neither is created
 * here. Entering a hundred evenings means the people and the rooms exist long before, and a
 * form that could invent them would invent a second Max Muster on the first typo.
 */
@Controller
@RequestMapping("/event/past")
public class PastEventController {

    /** What an evening that is over can be. Everything before it is a plan, not a memory. */
    private static final List<EventStatus> ENDINGS =
            List.of(EventStatus.DONE, EventStatus.CANCELLED);

    /**
     * The fields this form is made of, named once. What goes to the page carries every one
     * of them whether it was sent or not: a template that asks a map for a key it has not
     * got does not render an empty field, it fails.
     */
    private static final List<String> FIELDS = List.of("date", "startsAt", "mode", "status",
            "speakerId", "title", "abstractText", "announcedBio", "locationId",
            "addressPosition");

    private final ManageEvents events;
    private final ManageSpeakers speakers;
    private final ManageLocations locations;
    private final ErrorMessages errors;

    public PastEventController(ManageEvents events, ManageSpeakers speakers,
                               ManageLocations locations, ErrorMessages errors) {
        this.events = events;
        this.speakers = speakers;
        this.locations = locations;
        this.errors = errors;
    }

    @GetMapping
    public String form(Model model) {
        Map<String, String> empty = every(Map.of("startsAt", "19:00"));
        fill(model, empty);
        return "event/past";
    }

    /**
     * The whole form as it was sent. Taken as a map rather than as nine parameters: every
     * field is a string on its way in, the names are the template's own, and what goes back
     * on a refusal is exactly what came — a list of nine to name them twice would carry no
     * more meaning than this does.
     */
    @PostMapping
    public String enter(@RequestParam Map<String, String> sent, Model model) {
        Map<String, String> form = every(sent);
        try {
            events.enterPast(new PastEvening(
                    FormValues.date(field(form, "date")),
                    FormValues.time(field(form, "startsAt")),
                    heldAs(field(form, "mode")),
                    ended(field(form, "status")),
                    chosen(field(form, "speakerId")),
                    field(form, "title"),
                    field(form, "abstractText"),
                    field(form, "announcedBio"),
                    venue(field(form, "locationId")),
                    addressAt(venue(field(form, "locationId")), field(form, "addressPosition"))));
            return "redirect:/event";
        } catch (RuleViolated e) {
            // The records know the rules; the form only has to say so in German and keep
            // what was typed.
            model.addAttribute("error", errors.german(e));
            fill(model, form);
            return "event/past";
        }
    }

    /**
     * The biography of whoever was just picked, for the field to open with. Its own little
     * route because the text depends on the select above it, and htmx swaps the one field
     * rather than the form — swapping the form would drop everything else mid-entry.
     *
     * <p>It replaces what stands there. Picking a different person means announcing that
     * person, and the copy is taken from them.
     */
    @GetMapping("/bio")
    public String bioOf(@RequestParam(defaultValue = "") String speakerId, Model model) {
        model.addAttribute("announcedBio", speakers.byId(chosenOrNone(speakerId))
                .map(Speaker::bio)
                .orElse(""));
        return "event/past :: announced-bio";
    }

    private static String field(Map<String, String> form, String name) {
        return form.get(name);
    }

    /** Every field of the form, empty where nothing was sent. */
    private static Map<String, String> every(Map<String, String> sent) {
        Map<String, String> complete = new LinkedHashMap<>();
        FIELDS.forEach(name -> complete.put(name, sent.getOrDefault(name, "")));
        return complete;
    }

    /** Empty is the usual answer: most evenings were on site. */
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

    /** How it ended. Empty means it took place, which is what a backlog is mostly made of. */
    private static EventStatus ended(String status) {
        if (status == null || status.isBlank()) {
            return EventStatus.DONE;
        }
        EventStatus chosen = EventStatus.valueOf(status.strip());
        if (!ENDINGS.contains(chosen)) {
            throw new RuleViolated(Rule.EVENT_IS_NOT_OVER, chosen);
        }
        return chosen;
    }

    /** The person who spoke. Not optional: an evening without one is not an evening. */
    private static Long chosen(String speakerId) {
        Long picked = chosenOrNone(speakerId);
        if (picked == null) {
            throw new RuleViolated(Rule.NO_SPEAKER_CHOSEN);
        }
        return picked;
    }

    private static Long chosenOrNone(String speakerId) {
        if (speakerId == null || speakerId.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(speakerId.strip());
        } catch (NumberFormatException e) {
            throw new RuleViolated(Rule.NO_SPEAKER_CHOSEN);
        }
    }

    /**
     * Which of the venue's addresses the evening sat at. A place that moved keeps the old
     * one, and an evening from before the move points at it rather than at today's.
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
        locations.byId(place).orElseThrow(() -> new RuleViolated(Rule.NOT_FOUND))
                .addressAt(picked);
        return picked;
    }

    /**
     * The addresses of the place that was just picked. Its own little route because the
     * second select depends on the first, and htmx swaps it rather than the whole form.
     */
    @GetMapping("/addresses")
    public String addressesOf(@RequestParam(defaultValue = "") String locationId, Model model) {
        Long place = venue(locationId);
        model.addAttribute("place", place == null ? null : locations.byId(place).orElse(null));
        model.addAttribute("submitted", Map.of("addressPosition", ""));
        return "event/past :: venue-address";
    }

    /** Empty means the place was not written down; the evening then cannot be DONE. */
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

    private void fill(Model model, Map<String, String> submitted) {
        model.addAttribute("submitted", submitted);
        model.addAttribute("speakers", speakers.all());
        model.addAttribute("locations", locations.all());
        model.addAttribute("modes", EventMode.values());
        model.addAttribute("endings", ENDINGS);
        model.addAttribute("announcedBio", submitted.get("announcedBio"));
        // The address select needs the place that is picked; on a fresh form there is none.
        Long place = venue(submitted.get("locationId"));
        model.addAttribute("place", place == null ? null : locations.byId(place).orElse(null));
    }
}
