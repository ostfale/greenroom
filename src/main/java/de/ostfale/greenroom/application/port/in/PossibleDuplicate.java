package de.ostfale.greenroom.application.port.in;

import de.ostfale.greenroom.domain.speakers.Speaker;

/**
 * Somebody already stored who could be the person being typed in right now.
 *
 * <p>A warning, never a refusal: two people may carry the same name, and the second entry
 * is sometimes exactly what somebody wants. The flags say what matched, so the page can
 * name it instead of leaving the reader to compare.
 *
 * @param speaker   who is already there
 * @param sameEmail the address is the one being typed in — then it is the same person
 * @param sameName  the name is the one being typed in — then it probably is
 */
public record PossibleDuplicate(Speaker speaker, boolean sameEmail, boolean sameName) {
}
