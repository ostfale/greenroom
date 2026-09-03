package de.ostfale.greenroom.application.port.in;

import de.ostfale.greenroom.domain.events.EventMode;
import de.ostfale.greenroom.domain.events.EventStatus;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * What has to be typed in to write down an evening that already took place.
 *
 * <p>The one command record in this application, and the reason it earns its place: the
 * fields belong to four different records — the event, its talk, the person and the
 * biography that talk announced — and there is no single aggregate that could carry them
 * together before any of them is stored.
 *
 * <p>{@code announcedBio} is the biography as it stood back then, and it goes onto the
 * talk, never onto the speaker: the person of today is not who the flyer described.
 */
public record PastEvening(
        LocalDate date,
        LocalTime startsAt,
        EventMode mode,
        EventStatus status,
        String speakerName,
        String speakerEmail,
        String title,
        String abstractText,
        String announcedBio,
        Long locationId) {
}
