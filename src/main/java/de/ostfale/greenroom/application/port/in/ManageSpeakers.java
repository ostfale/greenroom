package de.ostfale.greenroom.application.port.in;

import de.ostfale.greenroom.domain.speaker.Speaker;

import java.util.List;

/**
 * Everything the web adapter needs to keep the list of speakers. The aggregate itself is
 * the argument and the result — there is no command record that would only be mapped onto
 * the same fields again.
 */
public interface ManageSpeakers {

    List<Speaker> all();

    /** Speakers whose name or company contains the fragment; all of them if it is blank. */
    List<Speaker> matching(String fragment);

    /** Stores a speaker that has no id yet and returns it with the id it was given. */
    Speaker add(Speaker speaker);
}
