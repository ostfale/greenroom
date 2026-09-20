package de.ostfale.greenroom.application.port.in;

import java.time.LocalDate;

/**
 * One line of what a person has talked about: which evening, what it was called, where.
 *
 * <p>Read off three records — the event, its talk and the place — and carried together
 * because the question it answers needs all three and nothing else of them: has this person
 * spoken here before, how long ago, and about what.
 *
 * <p>Planned evenings stand in the same list as the ones that have been. Whoever weighs an
 * invitation wants the date already agreed as much as the last one. A talk without a date
 * is an evening nobody has settled on yet, and one without a place is either online or not
 * that far along — both are shown, neither is a reason to leave the line out.
 *
 * @param eventId      the evening, so the line can lead to it
 * @param date         when it is or was, null while no day is settled
 * @param title        what the talk is called, null while it has no title
 * @param locationName where it takes place, null while there is no venue
 */
public record GivenTalk(Long eventId, LocalDate date, String title, String locationName) {
}
