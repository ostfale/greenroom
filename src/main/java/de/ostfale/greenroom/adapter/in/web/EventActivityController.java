package de.ostfale.greenroom.adapter.in.web;

import de.ostfale.greenroom.application.port.in.ManageActivities;
import de.ostfale.greenroom.domain.Rule;
import de.ostfale.greenroom.domain.RuleViolated;
import de.ostfale.greenroom.domain.activities.Activity;
import de.ostfale.greenroom.domain.activities.ActivityKind;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

/**
 * The history of an evening: a mail went out, or one came back. This is the only way a
 * line comes into being — nothing in the application writes one by itself.
 *
 * <p>Append-only, so there is no counterpart here that changes or drops a line. One that
 * was wrong is answered by the next one, and the port offers nothing else either.
 */
@Controller
@RequestMapping("/event/{id}/activity")
public class EventActivityController {

    private static final String TILE = "fragments/event-history :: event-history";

    private final ManageActivities activities;
    private final EventPage page;

    public EventActivityController(ManageActivities activities, EventPage page) {
        this.activities = activities;
        this.page = page;
    }

    @PostMapping
    public String append(@PathVariable Long id,
                         @RequestParam(defaultValue = "") String happenedOn,
                         @RequestParam(defaultValue = "") String kind,
                         @RequestParam(defaultValue = "") String what,
                         Model model) {
        return page.afterChanging(id, model, TILE, () ->
                activities.append(Activity.of(id, day(happenedOn), asKind(kind), what)));
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
        return FormValues.date(date);
    }
}
