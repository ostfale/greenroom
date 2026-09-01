package de.ostfale.greenroom.adapter.in.web;

import de.ostfale.greenroom.application.port.in.ImportPastEvents;
import de.ostfale.greenroom.application.port.in.ManageLocations;
import de.ostfale.greenroom.application.port.in.PastEvening;
import de.ostfale.greenroom.domain.events.EventMode;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The evenings of the last ten years, entered one form at a time. Nothing of how they were
 * organised is entered with them — who was asked and when is over, and the tool only has
 * to know that the evening took place.
 *
 * <p>Its own controller rather than another route on {@link EventController}: this builds a
 * finished evening in one step, where that one takes a topic apart into small changes.
 */
@Controller
@RequestMapping("/event/import")
public class EventImportController {

    private final ImportPastEvents imports;
    private final ManageLocations locations;

    public EventImportController(ImportPastEvents imports, ManageLocations locations) {
        this.imports = imports;
        this.locations = locations;
    }

    @GetMapping
    public String form(Model model) {
        fill(model, submitted("", "", "", "", "", "", ""));
        return "event/import";
    }

    @PostMapping
    public String enter(@RequestParam(defaultValue = "") String date,
                        @RequestParam(defaultValue = "") String mode,
                        @RequestParam(defaultValue = "") String speakerName,
                        @RequestParam(defaultValue = "") String speakerEmail,
                        @RequestParam(defaultValue = "") String title,
                        @RequestParam(defaultValue = "") String abstractText,
                        @RequestParam(defaultValue = "") String announcedBio,
                        @RequestParam(defaultValue = "") String locationId,
                        Model model) {
        try {
            imports.enter(new PastEvening(FormValues.date(date), how(mode), speakerName,
                    speakerEmail, title, abstractText, announcedBio, venue(locationId)));
            return "redirect:/event";
        } catch (IllegalArgumentException e) {
            // The records know the rules; the form only has to say so in German and keep
            // what was typed.
            model.addAttribute("error", message(e));
            fill(model, submitted(date, mode, speakerName, speakerEmail, title, abstractText,
                    announcedBio));
            return "event/import";
        }
    }

    /** Empty is the usual answer: most evenings were on site. */
    private static EventMode how(String mode) {
        if (mode == null || mode.isBlank()) {
            return null;
        }
        try {
            return EventMode.valueOf(mode.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("PastEvening :: unknown mode: " + mode);
        }
    }

    /** Empty means the place was not written down; the evening then stops before DONE. */
    private static Long venue(String locationId) {
        if (locationId == null || locationId.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(locationId.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("PastEvening :: no location was chosen");
        }
    }

    private static String message(IllegalArgumentException e) {
        String reason = e.getMessage() == null ? "" : e.getMessage();
        if (reason.contains("not a date")) {
            return "Das Datum konnte nicht gelesen werden.";
        }
        if (reason.contains("needs a date")) {
            return "Ein vergangener Abend braucht sein Datum.";
        }
        if (reason.contains("needs an email")) {
            return "Die E-Mail-Adresse gehört dazu — an ihr wird der Referent über die Jahre wiedererkannt.";
        }
        if (reason.contains("needs a name")) {
            return "Bitte den Namen des Referenten eintragen.";
        }
        return "Der Abend konnte nicht übernommen werden.";
    }

    private void fill(Model model, Map<String, String> submitted) {
        model.addAttribute("submitted", submitted);
        model.addAttribute("locations", locations.all());
        model.addAttribute("modes", EventMode.values());
    }

    private static Map<String, String> submitted(String date, String mode, String speakerName,
                                                 String speakerEmail, String title,
                                                 String abstractText, String announcedBio) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("date", date);
        values.put("mode", mode);
        values.put("speakerName", speakerName);
        values.put("speakerEmail", speakerEmail);
        values.put("title", title);
        values.put("abstractText", abstractText);
        values.put("announcedBio", announcedBio);
        return values;
    }
}
