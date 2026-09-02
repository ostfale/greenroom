package de.ostfale.greenroom.application.port.out;

import de.ostfale.greenroom.domain.activities.Activity;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;

import java.util.List;

/**
 * The entries written by hand. Deliberately not a {@code CrudRepository}: append-only is
 * the rule, so the port offers no way to delete or to read one back for editing. What is
 * not declared here cannot be called.
 *
 * <p>Entries go when the evening does — the foreign key cascades, which is the one deletion
 * that is allowed and is not this port's to perform.
 */
public interface ActivityRepository extends Repository<Activity, Long> {

    Activity save(Activity activity);

    /** Oldest first: a history is read forwards. */
    @Query("select * from activity where event_id = :eventId order by happened_on, id")
    List<Activity> findByEvent(Long eventId);
}
