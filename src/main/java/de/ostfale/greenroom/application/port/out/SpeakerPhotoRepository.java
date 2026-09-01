package de.ostfale.greenroom.application.port.out;

import de.ostfale.greenroom.domain.speakers.SpeakerPhoto;
import org.springframework.data.repository.ListCrudRepository;

import java.util.Optional;

/** Speaker photos, kept apart from the speaker so a list never loads them. */
public interface SpeakerPhotoRepository extends ListCrudRepository<SpeakerPhoto, Long> {

    Optional<SpeakerPhoto> findBySpeakerId(Long speakerId);

    void deleteBySpeakerId(Long speakerId);
}
