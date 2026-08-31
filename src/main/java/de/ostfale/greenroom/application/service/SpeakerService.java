package de.ostfale.greenroom.application.service;

import de.ostfale.greenroom.application.port.in.ManageSpeakers;
import de.ostfale.greenroom.application.port.out.SpeakerRepository;
import de.ostfale.greenroom.domain.speaker.Speaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class SpeakerService implements ManageSpeakers {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final SpeakerRepository speakerRepository;

    public SpeakerService(SpeakerRepository speakerRepository) {
        this.speakerRepository = speakerRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Speaker> all() {
        var allSpeaker = speakerRepository.findAllByOrderByNameAsc();
        log.debug("SpeakerService :: all speakers {}", allSpeaker);
        return allSpeaker;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Speaker> matching(String fragment) {
        return fragment == null || fragment.isBlank() ? all() : speakerRepository.search(fragment.strip());
    }

    @Override
    public Speaker add(Speaker speaker) {
        if (speaker.id() != null) {
            throw new IllegalArgumentException("SpeakerService :: this speaker is already stored");
        }
        log.debug("SpeakerService :: add speaker {}", speaker.name());
        return speakerRepository.save(speaker);
    }
}
