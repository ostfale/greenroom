package de.ostfale.greenroom.adapter.in.web;

import de.ostfale.greenroom.application.port.in.ManageEvents;
import de.ostfale.greenroom.application.port.in.ManageLocations;
import de.ostfale.greenroom.application.port.in.PastEvening;
import de.ostfale.greenroom.domain.Rule;
import de.ostfale.greenroom.domain.RuleViolated;
import de.ostfale.greenroom.domain.events.EventMode;
import de.ostfale.greenroom.domain.events.EventStatus;
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
 */
@Controller
@RequestMapping("/event/past")
public class PastEventController {

    /** What an evening that is over can be. Everything before it is a plan, not a memory. */
    private static final List<EventStatus> ENDINGS =
            List.of(EventStatus.DONE, EventStatus.CANCELLED);

    private final ManageEvents events;
    private final ManageLocations locations;
    private final ErrorMessages errors;

    public PastEventController(ManageEvents events, ManageLocations locations,
                               ErrorMessages errors) {
        this.events = events;
        this.locations = locations;
        this.errors = errors;
    }

    @GetMapping
    public String form(Model model) {
        fill(model, submitted("", "", "", "", "", "", "", ""));
        return "event/past";
    }

    @PostMapping
    public String enter(@RequestParam(defaultValue = "") String date,
                        @RequestParam(defaultValue = "") String mode,
                        @RequestParam(defaultValue = "") String status,
                        @RequestParam(defaultValue = "") String speakerName,
                        @RequestParam(defaultValue = "") String speakerEmail,
                        @RequestParam(defaultValue = "") String title,
                        @RequestParam(defaultValue = "") String abstractText,
                        @RequestParam(defaultValue = "") String announcedBio,
                        @RequestParam(defaultValue = "") String locationId,
                        Model model) {
        try {
            events.enterPast(new PastEvening(FormValues.date(date), heldAs(mode), ended(status),
                    speakerName, speakerEmail, title, abstractText, announcedBio,
                    venue(locationId)));
            return "redirect:/event";
        } catch (RuleViolated e) {
            // The records know the rules; the form only has to say so in German and keep
            // what was typed.
            model.addAttribute("error", errors.german(e));
            fill(model, submitted(date, mode, status, speakerName, speakerEmail, title,
                    abstractText, announcedBio));
            return "event/past";
        }
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
        model.addAttribute("locations", locations.all());
        model.addAttribute("modes", EventMode.values());
        model.addAttribute("endings", ENDINGS);
    }

    private static Map<String, String> submitted(String date, String mode, String status,
                                                 String speakerName, String speakerEmail,
                                                 String title, String abstractText,
                                                 String announcedBio) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("date", date);
        values.put("mode", mode);
        values.put("status", status);
        values.put("speakerName", speakerName);
        values.put("speakerEmail", speakerEmail);
        values.put("title", title);
        values.put("abstractText", abstractText);
        values.put("announcedBio", announcedBio);
        return values;
    }
}
