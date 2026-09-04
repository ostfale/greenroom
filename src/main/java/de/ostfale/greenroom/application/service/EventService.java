package de.ostfale.greenroom.application.service;

import de.ostfale.greenroom.application.port.in.EventFilter;
import de.ostfale.greenroom.application.port.in.ManageEvents;
import de.ostfale.greenroom.application.port.in.PastEvening;
import de.ostfale.greenroom.application.port.out.EventRepository;
import de.ostfale.greenroom.application.port.out.SpeakerRepository;
import de.ostfale.greenroom.domain.Rule;
import de.ostfale.greenroom.domain.RuleViolated;
import de.ostfale.greenroom.domain.events.Event;
import de.ostfale.greenroom.domain.events.EventStatus;
import de.ostfale.greenroom.domain.events.Talk;
import de.ostfale.greenroom.domain.events.TalkSpeaker;
import de.ostfale.greenroom.domain.speakers.Speaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class EventService implements ManageEvents {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final EventRepository eventRepository;
    private final SpeakerRepository speakerRepository;

    public EventService(EventRepository eventRepository, SpeakerRepository speakerRepository) {
        this.eventRepository = eventRepository;
        this.speakerRepository = speakerRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Event> all() {
        var allEvents = eventRepository.allNewestFirst();
        log.debug("EventService :: found {} events in total ", allEvents.size());
        return allEvents;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Event> matching(EventFilter filter) {
        // In memory, on the list that was loaded anyway: a few hundred evenings, and a
        // query assembled from five optional pieces would be the bigger thing by far.
        var selected = all().stream().filter(filter::matches).toList();
        log.debug("EventService :: {} of the events match", selected.size());
        return selected;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Event> clashesWith(Event event) {
        if (event.date() == null) {
            return List.of();
        }
        List<Event> sameDay = eventRepository.findByDate(event.date()).stream()
                .filter(other -> !other.id().equals(event.id()))
                .filter(other -> !other.status().isClosed())
                .toList();
        log.debug("EventService :: {} other events on {}", sameDay.size(), event.date());
        return sameDay;
    }

    @Override
    public Event enterPast(PastEvening past) {
        Speaker speaker = speakerRepository.findById(past.speakerId())
                .orElseThrow(() -> new RuleViolated(Rule.NO_SPEAKER_CHOSEN));
        Talk talk = Talk.by(TalkSpeaker.of(speaker.id()).withAnnouncedBio(past.announcedBio()))
                .withTitle(past.title())
                .withAbstract(past.abstractText())
                .withStartsAt(past.startsAt());
        // Straight at the status it ended in. The record still checks what that status
        // promises; only the walk through the chain is left out, and retracing a planning
        // that is ten years over would be ceremony.
        Event evening = new Event(null, past.date(), null, null, null, past.status(),
                past.mode(), past.locationId(), past.addressPosition(), List.of(talk));
        log.debug("EventService :: past evening on {} as {}", past.date(), past.status());
        return eventRepository.save(evening);
    }

    @Override
    public Event add(Event event) {
        if (event.id() != null) {
            throw new IllegalArgumentException("EventService :: this event is already stored");
        }
        log.debug("EventService :: add event on {}", event.date());
        return eventRepository.save(event);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Event> byId(Long id) {
        return id == null ? Optional.empty() : eventRepository.findById(id);
    }

    @Override
    public Event change(Event event) {
        if (event.id() == null) {
            throw new IllegalArgumentException("EventService :: this event was never stored");
        }
        log.debug("EventService :: change event {}", event.id());
        return eventRepository.save(event);
    }

    @Override
    public Event moveTo(Long eventId, EventStatus target) {
        Event event = known(eventId);
        log.debug("EventService :: move event {} from {} to {}", eventId, event.status(), target);
        return eventRepository.save(event.moveTo(target));
    }

    private Event known(Long eventId) {
        return byId(eventId).orElseThrow(() ->
                new IllegalArgumentException("EventService :: there is no event " + eventId));
    }

    @Override
    public Event addTalk(Long eventId, Talk talk) {
        log.debug("EventService :: add a talk to event {}", eventId);
        return eventRepository.save(known(eventId).withAdditionalTalk(talk));
    }

    @Override
    public Event changeTalk(Long eventId, int position, String title, String abstractText,
                           LocalTime startsAt, List<String> tags, List<String> announcedBios) {
        Event event = known(eventId);
        Talk talk = event.talkAt(position).withTitle(title).withAbstract(abstractText)
                .withStartsAt(startsAt)
                .withTags(tags == null ? List.of() : tags);
        talk = talk.withSpeakers(announced(talk.speakers(), announcedBios));
        log.debug("EventService :: change talk {} of event {}", position, eventId);
        return eventRepository.save(event.withTalkChanged(position, talk));
    }

    /**
     * The biographies as the form sent them back, one per speaker and in their order. A
     * list that does not match is left alone: the page was stale, and the speakers of a
     * talk are not what this form is allowed to move.
     */
    private static List<TalkSpeaker> announced(List<TalkSpeaker> speakers, List<String> bios) {
        if (bios == null || bios.size() != speakers.size()) {
            return speakers;
        }
        List<TalkSpeaker> rewritten = new ArrayList<>();
        for (int i = 0; i < speakers.size(); i++) {
            rewritten.add(speakers.get(i).withAnnouncedBio(bios.get(i)));
        }
        return rewritten;
    }

    @Override
    public Event removeTalk(Long eventId, int position) {
        log.debug("EventService :: remove talk {} from event {}", position, eventId);
        return eventRepository.save(known(eventId).withTalkRemoved(position));
    }
}
