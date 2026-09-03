package de.ostfale.greenroom.application.service;

import de.ostfale.greenroom.application.port.in.ManageSpeakers;
import de.ostfale.greenroom.application.port.out.EventRepository;
import de.ostfale.greenroom.application.port.out.ScaleImages;
import de.ostfale.greenroom.application.port.out.SpeakerPhotoRepository;
import de.ostfale.greenroom.application.port.out.SpeakerRepository;
import de.ostfale.greenroom.domain.Rule;
import de.ostfale.greenroom.domain.RuleViolated;
import de.ostfale.greenroom.domain.speakers.Speaker;
import de.ostfale.greenroom.domain.speakers.SpeakerPhoto;
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

    /** Big enough for the detail page on a sharp screen, small enough to forget about. */
    private static final int PHOTO_EDGE = 600;

    private final SpeakerRepository speakerRepository;
    private final SpeakerPhotoRepository photoRepository;
    private final ScaleImages images;
    private final EventRepository eventRepository;

    public SpeakerService(SpeakerRepository speakerRepository,
                          SpeakerPhotoRepository photoRepository,
                          ScaleImages images,
                          EventRepository eventRepository) {
        this.speakerRepository = speakerRepository;
        this.photoRepository = photoRepository;
        this.images = images;
        this.eventRepository = eventRepository;
    }

    @Override
    public Speaker change(Speaker speaker) {
        if (speaker.id() == null) {
            throw new IllegalArgumentException("SpeakerService :: this speaker has never been stored");
        }
        return speakerRepository.save(speaker);
    }

    @Override
    public void remove(Long id) {
        // Asked before deleting, so the page can name the reason instead of showing a
        // constraint violation. The foreign key stays as the last word.
        if (eventRepository.isOnATalk(id)) {
            throw new RuleViolated(Rule.SPEAKER_IS_ANNOUNCED_ON_A_TALK);
        }
        speakerRepository.deleteById(id);
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
        // contentType is what the browser claimed; the scaler trusts the bytes instead.
        if (byId(speakerId).isEmpty()) {
            throw new RuleViolated(Rule.NOT_FOUND, speakerId);
        }
        // Shrunk before it is stored, and re-encoded as JPEG on the way. Reading the bytes
        // is also the better check: a PDF renamed to .png passes any content type, not this.
        byte[] small = images.toJpegAtMost(data, PHOTO_EDGE);
        // One picture per speaker: the new one takes the place of the old.
        photoRepository.deleteBySpeakerId(speakerId);
        return photoRepository.save(SpeakerPhoto.of(speakerId, "image/jpeg", small));
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
