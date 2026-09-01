package de.ostfale.greenroom.application.service;

import de.ostfale.greenroom.application.port.in.ImportPastEvents;
import de.ostfale.greenroom.application.port.in.PastEvening;
import de.ostfale.greenroom.application.port.out.EventRepository;
import de.ostfale.greenroom.application.port.out.SpeakerRepository;
import de.ostfale.greenroom.domain.events.Event;
import de.ostfale.greenroom.domain.events.EventStatus;
import de.ostfale.greenroom.domain.events.Talk;
import de.ostfale.greenroom.domain.events.TalkSpeaker;
import de.ostfale.greenroom.domain.speakers.Speaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class EventImportService implements ImportPastEvents {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final EventRepository eventRepository;
    private final SpeakerRepository speakerRepository;

    public EventImportService(EventRepository eventRepository, SpeakerRepository speakerRepository) {
        this.eventRepository = eventRepository;
        this.speakerRepository = speakerRepository;
    }

    @Override
    public Event enter(PastEvening past) {
        Speaker speaker = known(past);
        Talk talk = Talk.by(TalkSpeaker.of(speaker.id()).withAnnouncedBio(past.announcedBio()))
                .withTitle(past.title())
                .withAbstract(past.abstractText());
        log.debug("EventImportService :: past evening on {} with {}", past.date(), speaker.name());
        return eventRepository.save(asFarAsItGoes(past, talk));
    }

    /**
     * The address is the person: somebody who spoke before is found again rather than
     * written down twice. Only the name and the address are taken — everything else about
     * them is of today and has nothing to do with an evening ten years ago.
     */
    private Speaker known(PastEvening past) {
        String email = past.speakerEmail() == null ? "" : past.speakerEmail().strip();
        return speakerRepository.findByEmail(email)
                .orElseGet(() -> speakerRepository.save(Speaker.of(past.speakerName(), email)));
    }

    /**
     * Walks the state machine as far as the data carries it. Nothing is bypassed: an
     * evening without a venue stops where an evening without a venue stops.
     */
    private static Event asFarAsItGoes(PastEvening past, Talk talk) {
        Event evening = Event.draftFor(talk)
                .withDate(past.date())
                .withLocation(past.locationId());
        if (past.mode() != null) {
            evening = evening.withMode(past.mode());
        }
        evening = evening.moveTo(EventStatus.DATE_CONFIRMED);
        if (past.locationId() == null) {
            return evening;
        }
        evening = evening.moveTo(EventStatus.VENUE_CONFIRMED);
        return evening.allTalksAreReadyToPublish()
                ? evening.moveTo(EventStatus.PUBLISHED).moveTo(EventStatus.DONE)
                : evening;
    }
}
