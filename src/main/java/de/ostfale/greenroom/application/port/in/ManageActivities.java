package de.ostfale.greenroom.application.port.in;

import de.ostfale.greenroom.domain.activities.Activity;

import java.util.List;

/**
 * What happened to an evening, and the one way to add to it. There is no change and no
 * remove: an {@link Activity} is append-only, and the port says so by offering nothing else.
 */
public interface ManageActivities {

    /**
     * Writes one more line.
     *
     * @throws IllegalArgumentException if the entry is already stored
     */
    Activity append(Activity activity);

    /** The whole evening in one order, oldest first. */
    List<Activity> historyOf(Long eventId);
}
