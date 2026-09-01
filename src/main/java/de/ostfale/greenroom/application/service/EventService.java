package de.ostfale.greenroom.application.service;

import de.ostfale.greenroom.application.port.in.ManageEvents;
import de.ostfale.greenroom.application.port.out.EventRepository;
import de.ostfale.greenroom.domain.events.Event;
import de.ostfale.greenroom.domain.events.EventStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class EventService implements ManageEvents {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final EventRepository eventRepository;

    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
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
    public List<Event> allStillOpen() {
        var allOpenEvents = all().stream().filter(event -> !event.status().isClosed()).toList();
        log.debug("EventService :: found {} open events", allOpenEvents.size());
        return allOpenEvents;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Event> alreadyPlannedOn(LocalDate date) {
        List<Event> allEventsOnDate = date == null ? List.of() : eventRepository.findByDate(date);
        log.debug("EventService :: found {} events on {}", allEventsOnDate.size(), date);
        return allEventsOnDate;
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
}
