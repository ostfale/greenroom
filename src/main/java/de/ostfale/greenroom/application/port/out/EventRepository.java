package de.ostfale.greenroom.application.port.out;

import de.ostfale.greenroom.domain.event.Event;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.time.LocalDate;
import java.util.List;

/** Evenings, in the words the use cases use. Spring Data implements it. */
public interface EventRepository extends ListCrudRepository<Event, Long> {

    /** Newest evening first; the topics without a date sit at the end. */
    @Query("select * from event order by date desc nulls last, id desc")
    List<Event> allNewestFirst();

    /**
     * Everything already planned for that evening. Two events on the same date are a
     * warning in the use case, never a rejected invariant.
     */
    List<Event> findByDate(LocalDate date);
}
