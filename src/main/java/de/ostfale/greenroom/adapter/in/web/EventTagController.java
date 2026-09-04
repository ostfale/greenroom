package de.ostfale.greenroom.adapter.in.web;

import de.ostfale.greenroom.application.port.in.ManageEvents;
import de.ostfale.greenroom.domain.Rule;
import de.ostfale.greenroom.domain.RuleViolated;
import de.ostfale.greenroom.domain.events.Event;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * The words an evening is announced with. Copied from the list in the settings, never
 * referenced: renaming or deleting a tag later must not rewrite what an evening was.
 *
 * <p>Which words may be ticked is {@link EventPage}'s answer, because the tile shows the
 * maintained list plus whatever this evening already carries.
 */
@Controller
@RequestMapping("/event/{id}/tags")
public class EventTagController {

    private static final String TILE = "fragments/event-tags :: event-tags";

    private final ManageEvents events;
    private final EventPage page;

    public EventTagController(ManageEvents events, EventPage page) {
        this.events = events;
        this.page = page;
    }

    /** Nothing ticked is a valid answer: an evening may carry no word at all. */
    @PostMapping
    public String change(@PathVariable Long id,
                         @RequestParam(name = "tag", required = false) List<String> chosen,
                         Model model) {
        return page.afterChanging(id, model, TILE, () -> {
            Event known = events.byId(id).orElseThrow(() -> new RuleViolated(Rule.NOT_FOUND));
            events.change(known.withTags(chosen == null ? List.of() : chosen));
        });
    }
}
