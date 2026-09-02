package de.ostfale.greenroom.application.port.out;

import de.ostfale.greenroom.domain.activities.VenueInquiry;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;

/** The requests that went out to places. Spring Data implements it. */
public interface VenueInquiryRepository extends ListCrudRepository<VenueInquiry, Long> {

    /** Newest first: what went out last is what is being waited for. */
    @Query("select * from venue_inquiry where event_id = :eventId order by sent_at desc, id desc")
    List<VenueInquiry> findByEvent(Long eventId);

    /**
     * Whether that place was ever asked. The inquiry keeps pointing at it, so it is kept —
     * the same reason a speaker who once spoke is kept.
     */
    @Query("select exists(select 1 from venue_inquiry where location_id = :locationId)")
    boolean wasAsked(Long locationId);
}
