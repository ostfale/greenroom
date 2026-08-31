package de.ostfale.greenroom.application.service;

import de.ostfale.greenroom.application.port.in.ManageSpeakers;
import de.ostfale.greenroom.application.port.out.SpeakerPhotoRepository;
import de.ostfale.greenroom.application.port.out.SpeakerRepository;
import de.ostfale.greenroom.domain.speaker.Speaker;
import de.ostfale.greenroom.domain.speaker.SpeakerPhoto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class SpeakerService implements ManageSpeakers {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final SpeakerRepository speakerRepository;
    private final SpeakerPhotoRepository photoRepository;

    public SpeakerService(SpeakerRepository speakerRepository, SpeakerPhotoRepository photoRepository) {
        this.speakerRepository = speakerRepository;
        this.photoRepository = photoRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Speaker> byId(Long id) {
        return id == null ? Optional.empty() : speakerRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SpeakerPhoto> photoOf(Long speakerId) {
        return speakerId == null ? Optional.empty() : photoRepository.findBySpeakerId(speakerId);
    }

    @Override
    public Speaker add(Speaker speaker, String photoContentType, byte[] photoData) {
        Speaker stored = add(speaker);
        if (photoData != null && photoData.length > 0) {
            // Runs in the same transaction, so a refused picture takes the speaker with it.
            storePhoto(stored.id(), photoContentType, photoData);
        }
        return stored;
    }

    @Override
    public SpeakerPhoto storePhoto(Long speakerId, String contentType, byte[] data) {
        if (byId(speakerId).isEmpty()) {
            throw new IllegalArgumentException("SpeakerService :: there is no speaker " + speakerId);
        }
        // One picture per speaker: the new one takes the place of the old.
        photoRepository.deleteBySpeakerId(speakerId);
        return photoRepository.save(SpeakerPhoto.of(speakerId, contentType, data));
    }

    @Override
    public void removePhoto(Long speakerId) {
        photoRepository.deleteBySpeakerId(speakerId);
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
