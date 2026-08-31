package de.ostfale.greenroom.application.port.in;

import de.ostfale.greenroom.domain.speaker.Speaker;
import de.ostfale.greenroom.domain.speaker.SpeakerPhoto;

import java.util.List;
import java.util.Optional;

/**
 * Everything the web adapter needs to keep the list of speakers. The aggregate itself is
 * the argument and the result — there is no command record that would only be mapped onto
 * the same fields again.
 */
public interface ManageSpeakers {

    List<Speaker> all();

    /** Speakers whose name or company contains the fragment; all of them if it is blank. */
    List<Speaker> matching(String fragment);

    Optional<Speaker> byId(Long id);

    /** Stores a speaker that has no id yet and returns it with the id it was given. */
    Speaker add(Speaker speaker);

    /**
     * The same, with a picture right away. Both go in one transaction: a file we cannot
     * accept leaves no half-entered speaker behind. Empty data means no picture.
     */
    Speaker add(Speaker speaker, String photoContentType, byte[] photoData);

    /** The picture of that speaker, if one was ever uploaded. */
    Optional<SpeakerPhoto> photoOf(Long speakerId);

    /** Puts a picture on the speaker, replacing whatever was there. */
    SpeakerPhoto storePhoto(Long speakerId, String contentType, byte[] data);

    void removePhoto(Long speakerId);
}
