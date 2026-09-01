package de.ostfale.greenroom.application.port.in;

import de.ostfale.greenroom.domain.events.EventMode;

import java.time.LocalDate;

/**
 * What has to be typed in to enter an evening that already happened.
 *
 * <p>A command, not a second model: the fields belong to four different records — the
 * event, its talk, the person and the biography that talk announced — and there is no
 * single aggregate that could carry them together before they are stored.
 *
 * <p>{@code announcedBio} is the biography as it stood back then, and it goes onto the
 * talk, never onto the speaker: the person of today is not who the flyer described.
 */
public record PastEvening(
        LocalDate date,
        EventMode mode,
        String speakerName,
        String speakerEmail,
        String title,
        String abstractText,
        String announcedBio,
        Long locationId) {
}
