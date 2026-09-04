package de.ostfale.greenroom.adapter.in.web;

import de.ostfale.greenroom.application.port.in.ManageEvents;
import de.ostfale.greenroom.domain.events.Talk;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * The talks of an evening. A talk is never entered on its own: it comes into being
 * together with the person who gives it, because that is how a talk is found.
 *
 * <p>Its own controller rather than four more routes on {@link EventController}: the tile
 * is swapped by itself, it answers with itself, and what it needs from the evening is the
 * id in the route. Everything the page around it shows comes from {@link EventPage}.
 */
@Controller
@RequestMapping("/event/{id}/talk")
public class EventTalkController {

    /** Every change to the talks answers with the same list. */
    private static final String TILE = "fragments/event-talks :: event-talks";

    private final ManageEvents events;
    private final EventPage page;

    public EventTalkController(ManageEvents events, EventPage page) {
        this.events = events;
        this.page = page;
    }

    /** A further talk. Like the first one, it comes into being with its speaker. */
    @PostMapping
    public String add(@PathVariable Long id,
                      @RequestParam(defaultValue = "") String speakerId,
                      @RequestParam(defaultValue = "") String title,
                      @RequestParam(defaultValue = "") String startsAt,
                      Model model) {
        return page.afterChanging(id, model, TILE, () ->
                events.addTalk(id, Talk.by(page.announced(speakerId))
                        .withTitle(title)
                        .withStartsAt(FormValues.time(startsAt))));
    }

    /**
     * Title, abstract, the words it is filed under, the hour it begins at, and the
     * biographies this evening announces its speakers with. Which people give it is not
     * this form's to change.
     *
     * <p>Nothing ticked sends no parameter at all, which is why the tags may be missing and
     * missing means none. A checkbox that is off has no other way of saying so.
     */
    @PostMapping("/{position}")
    public String change(@PathVariable Long id,
                         @PathVariable int position,
                         @RequestParam(defaultValue = "") String title,
                         @RequestParam(defaultValue = "") String abstractText,
                         @RequestParam(defaultValue = "") String startsAt,
                         @RequestParam(name = "tag", required = false) List<String> tags,
                         @RequestParam(name = "announcedBio", required = false) List<String> bios,
                         Model model) {
        return page.afterChanging(id, model, TILE, () ->
                events.changeTalk(id, position, title, abstractText,
                        FormValues.time(startsAt), tags, bios));
    }

    /** The last one cannot go: an evening without a talk is not an evening. */
    @PostMapping("/{position}/remove")
    public String remove(@PathVariable Long id, @PathVariable int position, Model model) {
        return page.afterChanging(id, model, TILE, () -> events.removeTalk(id, position));
    }
}
