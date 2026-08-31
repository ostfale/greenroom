package de.ostfale.greenroom.application.service;

import de.ostfale.greenroom.application.port.in.ManageSpeakers;
import de.ostfale.greenroom.application.port.out.SpeakerRepository;
import de.ostfale.greenroom.domain.speaker.Speaker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class SpeakerService implements ManageSpeakers {

    private final SpeakerRepository speakers;

    public SpeakerService(SpeakerRepository speakers) {
        this.speakers = speakers;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Speaker> all() {
        return speakers.findAllByOrderByNameAsc();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Speaker> matching(String fragment) {
        return fragment == null || fragment.isBlank() ? all() : speakers.search(fragment.strip());
    }

    @Override
    public Speaker add(Speaker speaker) {
        if (speaker.id() != null) {
            throw new IllegalArgumentException("SpeakerService :: this speaker is already stored");
        }
        return speakers.save(speaker);
    }
}
