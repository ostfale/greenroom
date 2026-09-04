package de.ostfale.greenroom.adapter.in.web;

import de.ostfale.greenroom.application.port.in.ManageActivities;
import de.ostfale.greenroom.application.port.in.ManageEvents;
import de.ostfale.greenroom.application.port.in.ManageLocations;
import de.ostfale.greenroom.application.port.in.ManageSpeakers;
import de.ostfale.greenroom.application.port.in.ManageTags;
import de.ostfale.greenroom.domain.Rule;
import de.ostfale.greenroom.domain.RuleViolated;
import de.ostfale.greenroom.domain.activities.ActivityKind;
import de.ostfale.greenroom.domain.events.Event;
import de.ostfale.greenroom.domain.events.EventMode;
import de.ostfale.greenroom.domain.events.EventStatus;
import de.ostfale.greenroom.domain.events.TalkSpeaker;
import de.ostfale.greenroom.domain.locations.Location;
import de.ostfale.greenroom.domain.speakers.Speaker;
import de.ostfale.greenroom.domain.tags.Tag;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The page of one evening: everything it reads, and the way every tile on it answers.
 *
 * <p>Its own class because that page is not served by one controller any more. The talks,
 * the tags and the history each have theirs, and every one of them swaps a fragment that
 * is rendered against the whole model — a tile rendered against half a model does not
 * render at all. Written down once here, so that a tile added later cannot forget half of
 * it.
 *
 * <p>Not a {@code @ControllerAdvice} with a {@code @ModelAttribute}: this model belongs to
 * the one evening a route names, and only the routes of that page want it.
 */
@Component
class EventPage {

    private final ManageEvents events;
    private final ManageSpeakers speakers;
    private final ManageLocations locations;
    private final ManageTags tags;
    private final ManageActivities activities;
    private final ErrorMessages errors;

    EventPage(ManageEvents events, ManageSpeakers speakers, ManageLocations locations,
              ManageTags tags, ManageActivities activities, ErrorMessages errors) {
        this.events = events;
        this.speakers = speakers;
        this.locations = locations;
        this.tags = tags;
        this.activities = activities;
        this.errors = errors;
    }

    /**
     * Makes the change and answers with the tile it was made in, showing what is stored
     * now. A refusal is not an error page: the stored state comes back unchanged, with the
     * German for it beside it, and the page carries on.
     *
     * <p>This is what every tile does, which is why it is written here and not seven
     * times.
     */
    String afterChanging(Long eventId, Model model, String fragment, Runnable change) {
        try {
            change.run();
        } catch (RuleViolated e) {
            model.addAttribute("error", errors.german(e));
        }
        return tile(eventId, model, fragment);
    }

    /** The tile as it stands, with nothing changed. */
    String tile(Long eventId, Model model, String fragment) {
        events.byId(eventId).ifPresent(event -> show(model, event));
        return fragment;
    }

    /** Everything the page of that evening reads, whether it is served whole or in tiles. */
    void show(Model model, Event event) {
        List<Location> places = locations.all();
        model.addAttribute("event", event);
        model.addAttribute("transitions", event.status().allowedTargets());
        model.addAttribute("milestones", EventStatus.milestones());
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
        Map<Long, String> byName = known.stream()
                .collect(Collectors.toMap(Speaker::id, Speaker::name));
        model.addAttribute("speakerNames", byName);
        model.addAttribute("invitation",
                InvitationText.of(event, byName, errors.text("invitation.speaker")));
        Location host = locations.byId(event.locationId()).orElse(null);
        model.addAttribute("location", host);
        // The address select reads these; the same fragment is served on its own when the
        // place changes, and then they come from the request instead.
        model.addAttribute("place", host);
        model.addAttribute("chosenAddress", event.addressPosition());
    }

    /**
     * A talk speaker with the biography they have right now. The copy is taken here, at
     * the moment the person is put on the talk: what the evening announced stays, however
     * often they rewrite their bio afterwards.
     *
     * <p>It sits beside the model because the list of speakers is already here, and
     * because both forms that put somebody on a talk — the one that starts an evening and
     * the one that adds a second talk to it — reach into that list the same way.
     */
    TalkSpeaker announced(String speakerId) {
        return speakers.byId(FormValues.speakerId(speakerId))
                .map(TalkSpeaker::announcing)
                .orElseThrow(() -> new RuleViolated(Rule.NO_SPEAKER_CHOSEN));
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
}
